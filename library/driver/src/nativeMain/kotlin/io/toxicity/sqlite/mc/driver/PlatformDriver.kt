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
package io.toxicity.sqlite.mc.driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import app.cash.sqldelight.logs.LogSqliteDriver
import co.touchlab.sqliter.*
import co.touchlab.sqliter.interop.Logger
import io.toxicity.sqlite.mc.driver.config.*

public actual sealed class PlatformDriver actual constructor(private val args: Args): SqlDriver {

    private val driver: SqlDriver get() = args.logDriver ?: args.nativeDriver

    actual final override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        driver.addListener(queryKeys = queryKeys, listener = listener)
    }

    actual final override fun notifyListeners(vararg queryKeys: String) {
        driver.notifyListeners(queryKeys = queryKeys)
    }

    actual final override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        driver.removeListener(queryKeys = queryKeys, listener = listener)
    }

    actual final override fun currentTransaction(): Transacter.Transaction? {
        return driver.currentTransaction()
    }

    actual final override fun newTransaction(): QueryResult<Transacter.Transaction> {
        return driver.newTransaction()
    }

    actual final override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> {
        return driver.execute(identifier, sql, parameters, binders)
    }

    actual final override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> {
        return driver.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    actual final override fun close() {
        args.properties.clear()
        driver.close()
    }
    
    protected actual companion object {

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal actual fun FactoryConfig.create(keyPragma: MutablePragmas, rekeyPragma: MutablePragmas?): Args {

            val config = DatabaseConfiguration(
                name = dbName,
                version = if (schema.version > Int.MAX_VALUE) {
                    error("Schema version is larger than Int.MAX_VALUE: ${schema.version}.")
                } else {
                    schema.version.toInt()
                },
                create = { connection ->
                    wrapConnection(connection) { schema.create(it) }
                },
                upgrade = { connection, oldVersion, newVersion ->
                    wrapConnection(connection) {
                        schema.migrate(it, oldVersion.toLong(), newVersion.toLong(), callbacks = afterVersions)
                    }
                },
                inMemory = false,
                journalMode = JournalMode.WAL, // TODO: Move to FactoryConfig.platformOptions
                extendedConfig = DatabaseConfiguration.Extended( // TODO: Move to FactoryConfig.platformOptions
                    foreignKeyConstraints = false,
                    busyTimeout = 5_000,
                    pageSize = null,
                    synchronousFlag = null,
                    basePath = filesystemConfig.databasesDir.path,
                    recursiveTriggers = false,
                    lookasideSlotSize = -1,
                    lookasideSlotCount = -1,
                ),
                loggingConfig = DatabaseConfiguration.Logging(
                    logger = object : Logger {
                        override val eActive: Boolean = false
                        override val vActive: Boolean = false

                        override fun eWrite(message: String, exception: Throwable?) {}
                        override fun trace(message: String) {}
                        override fun vWrite(message: String) {}
                    },
                    verboseDataCalls = false,
                ),
                lifecycleConfig = DatabaseConfiguration.Lifecycle(
                    onCreateConnection = { conn ->
                        logger?.invoke("onCreateConnection - START")

                        keyPragma.toMCSQLStatements().forEach { statement ->
                            logger?.invoke("EXECUTE\n $statement")
                            conn.rawExecSql(statement)
                        }

                        // Have to check if the key actually worked before going further
                        // with rekeying to new config
                        val verify = "SELECT 1 FROM sqlite_schema;"
                        logger?.invoke("EXECUTE\n $verify")
                        conn.rawExecSql(verify)

                        if (!rekeyPragma.isNullOrEmpty()) {

                            rekeyPragma.toMCSQLStatements().forEach { statement ->
                                logger?.invoke("EXECUTE\n $statement")
                                conn.rawExecSql(statement)
                            }

                            logger?.invoke("EXECUTE\n $verify")
                            conn.rawExecSql(verify)

                            // rekey successful, swap out old for new
                            keyPragma.clear()
                            rekeyPragma.forEach { entry ->
                                if (entry.key is Pragma.MC.RE_KEY) {
                                    keyPragma[Pragma.MC.KEY] = entry.value
                                } else {
                                    keyPragma[entry.key] = entry.value
                                }
                            }
                            rekeyPragma.clear()
                        }

                        logger?.invoke("onCreateConnection - FINISH")
                    },
                    onCloseConnection = {  },
                ),
                encryptionConfig = DatabaseConfiguration.Encryption(null, null)
            )

            val manager: DatabaseManager = try {
                createDatabaseManager(config)
            } catch (t: Throwable) {
                keyPragma.clear()
                rekeyPragma?.clear()
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create DatabaseManager", t)
            }

            val driver = NativeSqliteDriver(manager, maxReaderConnections = 1) // TODO: Move to FactoryConfig.platformOptions

            try {
                // Try opening first connection to
                // ensure key + rekey succeeds.
                manager.withConnection {}
            } catch (t: Throwable) {
                keyPragma.clear()
                rekeyPragma?.clear()
                driver.close()
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create NativeSqliteDriver", t)
            }

            return Args(keyPragma, driver, logger?.let { LogSqliteDriver(driver, it) })
        }

        internal actual class Args(
            internal val properties: MutablePragmas,
            internal val nativeDriver: NativeSqliteDriver,
            internal val logDriver: LogSqliteDriver?,
        )
    }
}
