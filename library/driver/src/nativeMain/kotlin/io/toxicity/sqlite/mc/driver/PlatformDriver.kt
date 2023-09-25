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
import app.cash.sqldelight.db.*
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import app.cash.sqldelight.logs.LogSqliteDriver
import co.touchlab.sqliter.*
import co.touchlab.sqliter.interop.Logger
import co.touchlab.sqliter.interop.SqliteDatabasePointer
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
        driver.close()
        args.close()
    }
    
    protected actual companion object {

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal actual fun FactoryConfig.create(keyPragma: MutablePragmas, rekeyPragma: MutablePragmas?): Args {

            val config = DatabaseConfiguration(
                name = dbName,
                version = schema.versionInt(),
                create = schema.create(),
                upgrade = schema.upgrade(afterVersions),
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
                loggingConfig = NO_LOG,
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

            return Args(driver, logger?.let { LogSqliteDriver(driver, it) }, close = {
                keyPragma.clear()
                rekeyPragma?.clear()
            })
        }

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal actual fun FactoryConfig.create(opt: EphemeralOpt): Args {
            val config = DatabaseConfiguration(
                name = when (opt) {
                    is EphemeralOpt.InMemory -> null
                    is EphemeralOpt.Named -> dbName
                    is EphemeralOpt.Temporary -> ""
                },
                version = schema.versionInt(),
                create = schema.create(),
                upgrade = schema.upgrade(afterVersions),
                inMemory = when (opt) {
                    is EphemeralOpt.InMemory,
                    is EphemeralOpt.Named -> true
                    is EphemeralOpt.Temporary -> false
                },
                journalMode = JournalMode.WAL, // TODO: Move to FactoryConfig.platformOptions
                extendedConfig = DatabaseConfiguration.Extended( // TODO: Move to FactoryConfig.platformOptions
                    foreignKeyConstraints = false,
                    busyTimeout = 5_000,
                    pageSize = null,
                    synchronousFlag = null,
                    basePath = when (opt) {
                        is EphemeralOpt.InMemory,
                        is EphemeralOpt.Named -> null
                        is EphemeralOpt.Temporary -> ""
                    },
                    recursiveTriggers = false,
                    lookasideSlotSize = -1,
                    lookasideSlotCount = -1,
                ),
                loggingConfig = NO_LOG,
                lifecycleConfig = DatabaseConfiguration.Lifecycle(
                    onCreateConnection = {},
                    onCloseConnection = {},
                ),
            )

            val manager: DatabaseManager = try {
                createDatabaseManager(config)
            } catch (t: Throwable) {
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create DatabaseManager", t)
            }

            val connection = try {
                manager.createMultiThreadedConnection()
            } catch (t: Throwable) {
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create a static connection", t)
            }

            // SQLDelight has thread pools that will close
            // the connection if it is a purely in-memory/temporary
            // database, which is incorrect.
            //
            // See https://github.com/cashapp/sqldelight/issues/3241
            val nonCloseableConnection = object : DatabaseConnection {
                override val closed: Boolean get() = connection.closed
                override fun beginTransaction() { connection.beginTransaction() }
                override fun close() { /* no-op */ }
                override fun createStatement(sql: String): Statement = connection.createStatement(sql)
                override fun endTransaction() { connection.endTransaction() }
                override fun getDbPointer(): SqliteDatabasePointer = connection.getDbPointer()
                override fun rawExecSql(sql: String) { connection.rawExecSql(sql) }
                override fun setTransactionSuccessful() { connection.setTransactionSuccessful() }
            }

            val driver = NativeSqliteDriver(
                databaseManager = object : DatabaseManager {
                    // SQLDelight work around
                    //
                    // See https://github.com/cashapp/sqldelight/issues/3241#issuecomment-1732270263
                    override val configuration: DatabaseConfiguration = if (!config.inMemory) {
                        config.copy(inMemory = true)
                    } else {
                        config
                    }
                    override fun createMultiThreadedConnection(): DatabaseConnection = nonCloseableConnection
                    override fun createSingleThreadedConnection(): DatabaseConnection = nonCloseableConnection
                },
                maxReaderConnections = 1,
            )

            return Args(driver, logger?.let { LogSqliteDriver(driver, it) }, close = {
                try {
                    connection.close()
                } catch (_: Throwable) {}
            })
        }

        internal actual class Args(
            internal val nativeDriver: NativeSqliteDriver,
            internal val logDriver: LogSqliteDriver?,
            internal val close: () -> Unit,
        )

        private val NO_LOG = DatabaseConfiguration.Logging(
            logger = object : Logger {
                override val eActive: Boolean = false
                override val vActive: Boolean = false

                override fun eWrite(message: String, exception: Throwable?) {}
                override fun trace(message: String) {}
                override fun vWrite(message: String) {}
            },
            verboseDataCalls = false,
        )
    }
}

private fun SqlSchema<QueryResult.Value<Unit>>.versionInt(): Int {
    return if (version > Int.MAX_VALUE) {
        error("Schema version is larger than Int.MAX_VALUE: $version.")
    } else {
        version.toInt()
    }
}

private fun SqlSchema<QueryResult.Value<Unit>>.create(): (DatabaseConnection) -> Unit = { connection ->
    wrapConnection(connection) { create(it) }
}

private fun SqlSchema<QueryResult.Value<Unit>>.upgrade(
    afterVersions: Array<AfterVersion>
): (DatabaseConnection, Int, Int) -> Unit = { connection, oldVersion, newVersion ->
    wrapConnection(connection) {
        migrate(it, oldVersion.toLong(), newVersion.toLong(), callbacks = afterVersions)
    }
}
