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

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.encoding.core.use
import io.matthewnelson.encoding.core.util.LineBreakOutFeed
import io.toxicity.sqlite.mc.driver.config.Pragma
import io.toxicity.sqlite.mc.driver.config.encryption.Cipher
import io.toxicity.sqlite.mc.driver.internal.ext.buildMCConfigSQL
import org.sqlite.JDBC
import org.sqlite.jdbc4.JDBC4Connection
import org.sqlite.jdbc4.JDBC4Statement
import java.security.SecureRandom
import java.sql.SQLException
import java.sql.Statement
import java.util.Properties

internal class JDBCMC4Connection private constructor(
    url: String,
    fileName: String,
    prop: Properties,
): JDBC4Connection(url, fileName, prop) {

    // Can't actually set a value b/c SQLiteConfig
    // invokes createStatement from its constructor,
    // before this class' properties are initialized.
    //
    // All that can be done is set to non-null in order
    // to delegate back to JDBC4Connection after we
    // intercept the initial call to inject all the
    // cipher config SELECT statements and apply our key
    // PRAGMA.
    @Volatile
    private var initialized: Boolean? = null

    override fun createStatement(rst: Int, rsc: Int, rsh: Int): Statement {
        return if (initialized == null) {
            checkOpen()
            checkCursor(rst, rsc, rsh)

            initialized = true
            JDBCMC4Statement(this)
        } else {
            super.createStatement(rst, rsc, rsh)
        }
    }

    internal companion object {



        @JvmStatic
        @JvmSynthetic
        @Throws(SQLException::class)
        internal fun of(
            url: String?,
            prop: Properties?,
        ): JDBCMC4Connection? {
            if (!JDBC.isValidURL(url)) return null
            val trimmed = url?.trim() ?: return null

            val fileName = trimmed.substring(JDBCMC.PREFIX.length)

            val newProp = Properties()

            // Copy over all properties (as to not modify
            // the properties object that was passed).
            if (prop != null) {
                newProp.putAll(prop)
            }

            var cipher: Cipher? = null
            var hasKey = false
            var hasLegacy = false

            val sb = StringBuilder()
            val out = LineBreakOutFeed(interval = 64, out = { c -> sb.append(c) })
            JDBCMC4Statement.KEY_IDENTIFIER.forEach { c -> out.output(c) }

            Base64.Default.newEncoderFeed(out).use { feed ->

                for (pragma in Pragma.MC.ALL) {
                    val value = (newProp[pragma.name] as? String) ?: continue

                    val sqlStatement = when (pragma) {
                        is Pragma.MC.CIPHER -> {
                            // cipher is first in the Pragma.MC.ALL order,
                            // so will set this parameter first (if it's
                            // present).
                            cipher = Cipher.valueOfOrNull(value) ?: continue

                            pragma.name.buildMCConfigSQL(
                                transient = false,
                                arg2 = value,
                                arg3 = null,
                            )
                        }
                        is Pragma.MC.KEY -> {
                            hasKey = true
                            "PRAGMA ${pragma.name} = $value;"
                        }
                        else -> {
                            val arg3: Number = value.toIntOrNull() ?: continue

                            cipher?.name?.buildMCConfigSQL(
                                transient = false,
                                arg2 = pragma.name,
                                arg3 = arg3,
                            ) ?: continue
                        }
                    }

                    if (pragma is Pragma.MC.LEGACY) {
                        hasLegacy = true
                    }

                    // Encode each to base 64 with delimiter of .
                    sqlStatement.forEach { c -> feed.consume(c.code.toByte()) }
                    feed.flush()
                    out.output('.')
                }

                // TODO: Also check for if present and apply after PRAGMA key:
                //  PAGE_SIZE
                //  AUTO_VACUUM
                //  ENCODING
                //  See https://github.com/Willena/sqlite-jdbc-crypt/blob/4e5cd3bce9d34818c1413a638c824fe95117fa99/src/main/java/org/sqlite/SQLiteConfig.java#L238
            }

            val sbLen = sb.length

            val isUsingMC = cipher != null && hasKey && hasLegacy

            // Only add if it contains all 3 required cipher parameters
            if (isUsingMC) {
                // Remove all MC pragmas from newProp
                Pragma.MC.ALL.forEach {  pragma -> newProp.remove(pragma.name) }

                // Pass all of our statements in the "password" pragma
                // in order to catch them when SQLiteConfig.apply is called
                newProp["password"] = sb.toString()

                // Remove, just in case, as we want PRAGMA key to execute
                // with the expected format of `pragma key = '<encoded statements'
                // in order to decode the contents.
                newProp.remove("hexkey_mode")
            }

            sb.clear()
            repeat(sbLen) { sb.append(' ') }

            val connection = try {
                JDBCMC4Connection(url, fileName, newProp)
            } finally {
                // Also clear newProps which was copied over to another
                // new Properties object in SQLiteConfig.open
                newProp.clear()
            }

            if (isUsingMC) {
                // Remove key from the config's pragmaTable
                // as to not leak it elsewhere.
                connection.database
                    .config
                    .toProperties()
                    .remove("password")
            }

            return connection
        }
    }
}

private class JDBCMC4Statement(
    connection: JDBCMC4Connection
): JDBC4Statement(connection) {

    internal companion object {
        // 64 character random id which is used to ensure
        // any encoded PRAGMA key execution values are from
        // JDBCMCConnection. Will always be stripped and
        // removed upon execute invocation.
        //
        // MUST be 64 chars max, as that is the
        // line break interval set for the encoder.
        internal val KEY_IDENTIFIER: String by lazy {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            bytes.encodeToString(Base16)
        }
    }

    override fun execute(sql: String?): Boolean {
        if (sql == null || !sql.startsWith("pragma ${Pragma.MC.KEY.name} =", ignoreCase = true)) {
            return super.execute(sql)
        }

        // pragma key = 'KEY_IDENTIFIERbase64.encoded.cipher.statements.'
        val sqlStatements: List<String> = sql.substringAfter('\'').let {
            if (!it.startsWith(KEY_IDENTIFIER)) return super.execute(sql) else it
        }.substring(KEY_IDENTIFIER.length)
            .substringBeforeLast('\'')
            .split('.')
            .mapNotNull { encodedStatement ->
                if (encodedStatement.isBlank()) return@mapNotNull null

                val sb = StringBuilder()

                try {
                    Base64.Default.newDecoderFeed { b ->
                        sb.append(b.toInt().toChar())
                    }.use { feed ->
                        encodedStatement.forEach { c -> feed.consume(c) }
                    }
                } catch (e: EncodingException) {
                    throw SQLException("Failed to apply cipher parameters", e)
                }

                val sbLen = sb.length

                val statement = sb.toString()
                sb.clear()
                repeat(sbLen) { sb.append(' ') }

                statement.ifBlank { null }
            }

        for (statement in sqlStatements) {
            super.execute(statement)
        }

        return true
    }
}
