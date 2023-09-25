/*
 * Copyright (c) 2023 Toxicity
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.toxicity.sqlite.mc.driver.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.use
import io.matthewnelson.encoding.core.util.LineBreakOutFeed
import io.toxicity.sqlite.mc.driver.EphemeralOpt
import io.toxicity.sqlite.mc.driver.config.MutablePragmas
import io.toxicity.sqlite.mc.driver.config.Pragma
import io.toxicity.sqlite.mc.driver.config.toMCSQLStatements
import io.toxicity.sqlite.mc.driver.internal.ext.buildMCConfigSQL
import java.sql.SQLException
import java.util.Properties

internal class JDBCMCProperties private constructor(

    /**
     * [JdbcSqliteDriver] only accepts [JdbcSqliteDriver.IN_MEMORY] in order
     * to use a non-threaded connection manager (i.e. a static connection).
     * This is to work around such issues.
     *
     * See https://github.com/cashapp/sqldelight/pull/4656
     * */
    internal val ephemeralUrlSuffix: String,

    private val keyPragma: MutablePragmas?,
    @Volatile
    private var rekeyPragma: MutablePragmas?,
): Properties() {

    @Volatile
    private var isInitialized = keyPragma == null
    private val lock = Any()

    init {
        require(keyPragma?.containsKey(Pragma.MC.KEY) != false) {
            "keyPragma must contain Pragma.MC.KEY"
        }
        require(keyPragma?.containsKey(Pragma.MC.RE_KEY) != true) {
            "keyPragma cannot contain Pragma.MC.RE_KEY"
        }
        require(rekeyPragma?.containsKey(Pragma.MC.RE_KEY) != false) {
            "rekeyPragma must contain Pragma.MC.RE_KEY"
        }
        require(rekeyPragma?.containsKey(Pragma.MC.KEY) != true) {
            "rekeyPragma cannot contain Pragma.MC.KEY"
        }
    }

    // SQLite3Multiple ciphers requires setting of its config before
    // executing key or rekey pragmas, which should all take place
    // prior to anything else being executed.
    //
    // In order to interop with JDBC, all config statements and key/rekey
    // pragmas are base64 encoded and packed into the "password" pragma
    // so that when JDBC goes to apply it (which it does first thing), we
    // can decode all the statements and execute each one individually.
    @Throws(SQLException::class)
    internal fun withKeyProps(
        keyIdentifier: String,
        block: (keyProp: Properties) -> JDBCMC4Connection
    ): JDBCMC4Connection {
        if (isInitialized) return this.connect(block)

        return synchronized(lock) {
            if (isInitialized) return@synchronized this.connect(block)

            val keyProp = Properties()
            keyProp.putAll(this)

            if (!keyProp.containsKey("password")) {
                keyProp.packMCSqlStatements(keyIdentifier)
            }

            val conn = try {
                keyProp.connect(block)
            } finally {
                keyProp.clear()
            }

            if (keyPragma != null) {

                // If a successful rekey was performed, need
                // to migrate that cipher config over to keyPragma.
                rekeyPragma?.let { rekey ->
                    keyPragma.clear()

                    rekey.forEach { entry ->
                        if (entry.key is Pragma.MC.RE_KEY) {
                            keyPragma[Pragma.MC.KEY] = entry.value
                        } else {
                            keyPragma[entry.key] = entry.value
                        }
                    }

                    rekey.clear()
                    rekeyPragma = null
                }

                if (!this.containsKey("password")) {
                    // This was the first connection created with these
                    // properties. Rekeying (if performed) has completed
                    // and all cipher parameters have been migrated to keyPragma.
                    //
                    // Set "password" pragma indefinitely as to not need
                    // to encode the cipher parameters for every subsequent
                    // connection created.
                    this.packMCSqlStatements(keyIdentifier)
                }
            }

            isInitialized = true

            conn
        }
    }

    private fun Properties.connect(
        block: (prop: Properties) -> JDBCMC4Connection
    ): JDBCMC4Connection {
        val conn = block(this)

        if (keyPragma != null) {
            // Remove password pragma with all the cipher
            // statements as to not leak it elsewhere
            conn.database
                .config
                .toProperties()
                .remove("password")
        }

        return conn
    }

    @Throws(SQLException::class)
    private fun Properties.packMCSqlStatements(keyIdentifier: String) {
        if (keyPragma == null) return

        val keyStatements = try {
            keyPragma.toMCSQLStatements()
        } catch (e: IllegalArgumentException) {
            throw SQLException("Failed to create key statements", e)
        }

        val reKeyStatements = try {
            rekeyPragma?.toMCSQLStatements()
        } catch (e: IllegalArgumentException) {
            throw SQLException("Failed to create rekey statements", e)
        }

        val sb = StringBuilder()
        val out = LineBreakOutFeed(interval = 64, out = { c -> sb.append(c) })
        keyIdentifier.forEach { c -> out.output(c) }

        Base64.Default.newEncoderFeed(out).use { feed ->
            keyStatements.forEach { statement ->
                statement.forEach { c -> feed.consume(c.code.toByte()) }
                feed.flush()
                out.output('.')
            }

            // rekey statements will execute right after
            // key statements.
            if (reKeyStatements != null) {
                // Have to check if the key actually worked before going further
                // with rekeying to new config
                "SELECT 1 FROM sqlite_schema;".forEach { c -> feed.consume(c.code.toByte()) }
                feed.flush()
                out.output('.')

                reKeyStatements.forEach { statement ->
                    statement.forEach { c -> feed.consume(c.code.toByte()) }
                    feed.flush()
                    out.output('.')
                }
            }

            // If the cipher has changed, need to reset the non-transient
            // config settings after rekey event
            val rekeyPragma = rekeyPragma
            if (rekeyPragma != null && rekeyPragma[Pragma.MC.CIPHER] != keyPragma[Pragma.MC.CIPHER]) {
                // Must have a cipher, otherwise would have thrown earlier when producing rekey statements
                val cipher: String = rekeyPragma[Pragma.MC.CIPHER]!!

                for (pragma in Pragma.MC.ALL) {
                    val value = rekeyPragma[pragma] ?: continue

                    val sql = when (pragma) {
                        is Pragma.MC.CIPHER -> {
                            pragma.name.buildMCConfigSQL(
                                transient = false,
                                arg2 = value,
                                arg3 = null
                            )
                        }
                        is Pragma.MC.RE_KEY -> {
                            "PRAGMA key = $value;"
                        }
                        else -> {
                            cipher.buildMCConfigSQL(
                                transient = false,
                                arg2 = pragma.name,
                                arg3 = value.toIntOrNull() ?: continue
                            )
                        }
                    }

                    sql.forEach { c -> feed.consume(c.code.toByte()) }
                    feed.flush()
                    out.output('.')
                }
            }
        }

        val sbLen = sb.length

        // Pass all of our statements in the "password" pragma
        // in order to catch them when SQLiteConfig.apply is called
        this["password"] = sb.toString()

        // Remove, just in case, as we want PRAGMA key to execute
        // with the expected format of `pragma key = '<encoded statements'
        // in order to decode the contents.
        remove("hexkey_mode")

        sb.clear()
        repeat(sbLen) { sb.append(' ') }
    }

    override fun clear() {
        synchronized(lock) {
            keyPragma?.clear()
            rekeyPragma?.clear()
        }
        super.clear()
    }

    internal companion object {

        @JvmStatic
        @JvmSynthetic
        @Throws(IllegalArgumentException::class)
        internal fun of(
            keyPragma: MutablePragmas,
            rekeyPragma: MutablePragmas?,
        ): JDBCMCProperties = JDBCMCProperties(
            ephemeralUrlSuffix = "",
            keyPragma = keyPragma,
            rekeyPragma = rekeyPragma,
        )

        @JvmStatic
        @JvmSynthetic
        internal fun of(
            opt: EphemeralOpt
        ): JDBCMCProperties = JDBCMCProperties(
            ephemeralUrlSuffix = when (opt) {
                EphemeralOpt.InMemory -> ":memory:"
                EphemeralOpt.Temporary -> ""
            },
            keyPragma = null,
            rekeyPragma = null,
        )
    }
}
