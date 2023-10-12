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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.toxicity.sqlite.mc.driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.*
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import app.cash.sqldelight.logs.LogSqliteDriver
import co.touchlab.sqliter.*
import co.touchlab.sqliter.interop.Logger
import io.toxicity.sqlite.mc.driver.config.*
import io.toxicity.sqlite.mc.driver.config.MutableMCPragmas
import io.toxicity.sqlite.mc.driver.config.MCPragma
import io.toxicity.sqlite.mc.driver.config.toMCSQLStatements

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
        args.properties?.clear()
    }
    
    protected actual companion object {

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal actual fun FactoryConfig.create(keyPragma: MutableMCPragmas, rekeyPragma: MutableMCPragmas?): Args {
            val pragmas = pragmaConfig.filesystem.toMutableMap()

            val config = DatabaseConfiguration(
                name = dbName,
                version = schema.versionInt(),
                create = schema.create(),
                upgrade = schema.upgrade(afterVersions),
                inMemory = false,
                journalMode = pragmas.journalMode(),
                extendedConfig = DatabaseConfiguration.Extended(
                    foreignKeyConstraints = pragmas.foreignKeys(),
                    busyTimeout = pragmas.busyTimeout(),
                    pageSize = pragmas.pageSize(),
                    synchronousFlag = null,
                    basePath = filesystemConfig.databasesDir.path,
                    recursiveTriggers = pragmas.recursiveTriggers(),
                    lookasideSlotSize = -1,
                    lookasideSlotCount = -1,
                ),
                loggingConfig = NO_LOG,
                lifecycleConfig = DatabaseConfiguration.Lifecycle(
                    onCreateConnection = { conn ->
                        keyPragma.toMCSQLStatements().forEach { statement ->
                            conn.rawExecSql(statement)
                        }

                        if (!rekeyPragma.isNullOrEmpty()) {

                            rekeyPragma.toMCSQLStatements().forEach { statement ->
                                conn.rawExecSql(statement)
                            }

                            // rekey successful, swap out old for new
                            keyPragma.clear()
                            rekeyPragma.forEach { entry ->
                                if (entry.key is MCPragma.RE_KEY) {
                                    keyPragma[MCPragma.KEY] = entry.value
                                } else {
                                    keyPragma[entry.key] = entry.value
                                }
                            }
                            rekeyPragma.clear()
                        }

                        conn.rawExecSql("SELECT 1 FROM sqlite_schema")

                        pragmas.forEach { entry ->
                            conn.rawExecSql("PRAGMA ${entry.key} = ${entry.value}")
                        }
                    },
                    onCloseConnection = {},
                ),
            )

            val manager: DatabaseManager = try {
                val m = createDatabaseManager(config)
                // Try opening first connection to ensure the key
                // is correct and/or rekey worked.
                m.withConnection {}
                m
            } catch (t: Throwable) {
                keyPragma.clear()
                rekeyPragma?.clear()
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create DatabaseManager", t)
            }

            val driver = NativeSqliteDriver(manager, platformConfig.maxReaderConnections)

            return Args(keyPragma, driver, logger?.let { LogSqliteDriver(driver, it) })
        }

        @Throws(IllegalStateException::class)
        internal actual fun FactoryConfig.create(opt: EphemeralOpt): Args {
            val pragmas = pragmaConfig.ephemeral.toMutableMap()

            val config = DatabaseConfiguration(
                name = when (opt) {
                    EphemeralOpt.IN_MEMORY -> null
                    EphemeralOpt.NAMED -> dbName
                    EphemeralOpt.TEMPORARY -> ""
                },
                version = schema.versionInt(),
                create = schema.create(),
                upgrade = schema.upgrade(afterVersions),
                inMemory = when (opt) {
                    EphemeralOpt.IN_MEMORY,
                    EphemeralOpt.NAMED -> true
                    EphemeralOpt.TEMPORARY -> false
                },
                journalMode = pragmas.journalMode(),
                extendedConfig = DatabaseConfiguration.Extended(
                    foreignKeyConstraints = pragmas.foreignKeys(),
                    busyTimeout = pragmas.busyTimeout(),
                    pageSize = pragmas.pageSize(),
                    synchronousFlag = null,
                    basePath = when (opt) {
                        EphemeralOpt.IN_MEMORY,
                        EphemeralOpt.NAMED -> null
                        EphemeralOpt.TEMPORARY -> ""
                    },
                    recursiveTriggers = pragmas.recursiveTriggers(),
                    lookasideSlotSize = -1,
                    lookasideSlotCount = -1,
                ),
                loggingConfig = NO_LOG,
                lifecycleConfig = DatabaseConfiguration.Lifecycle(
                    onCreateConnection = { conn ->
                        pragmas.forEach { entry ->
                            conn.rawExecSql("PRAGMA ${entry.key} = ${entry.value}")
                        }
                    },
                    onCloseConnection = {},
                ),
            )

            val manager: DatabaseManager = try {
                createDatabaseManager(config)
            } catch (t: Throwable) {
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create DatabaseManager", t)
            }

            val driver = NativeSqliteDriver(
                databaseManager = if (!config.inMemory) {
                    // SQLDelight work around
                    //
                    // See https://github.com/cashapp/sqldelight/pull/4662
                    val copy = config.copy(inMemory = true)

                    object : DatabaseManager {
                        override val configuration: DatabaseConfiguration = copy
                        override fun createMultiThreadedConnection(): DatabaseConnection =
                            manager.createMultiThreadedConnection()
                        override fun createSingleThreadedConnection(): DatabaseConnection =
                            manager.createSingleThreadedConnection()
                    }
                } else {
                    manager
                },
                maxReaderConnections = 1,
            )

            try {
                // Establish initial connection to ensure any pragma statements passed
                // did not cause issue. NativeSqliteDriver will hold onto this connection
                // until driver.close has been invoked.
                driver.executeQuery(null, "PRAGMA user_version", mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
                }, 0, null).value
            } catch (t: Throwable) {
                driver.close()
                pragmas.clear()
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create a connection", t)
            }

            return Args(null, driver, logger?.let { LogSqliteDriver(driver, it) })
        }

        internal actual class Args(
            internal val properties: MutableMCPragmas?,
            internal val nativeDriver: NativeSqliteDriver,
            internal val logDriver: LogSqliteDriver?,
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

@Throws(IllegalStateException::class)
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

@Suppress("NOTHING_TO_INLINE")
private inline fun MutableMap<String, String>.journalMode(): JournalMode {
    return remove("journal_mode")?.let { JournalMode.forString(it) } ?: JournalMode.DELETE
}

@Suppress("NOTHING_TO_INLINE")
private inline fun MutableMap<String, String>.busyTimeout(): Int {
    return remove("busy_timeout")?.toIntOrNull() ?: 3_000
}

@Suppress("NOTHING_TO_INLINE")
private inline fun MutableMap<String, String>.pageSize(): Int? {
    return remove("page_size")?.toIntOrNull()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun MutableMap<String, String>.foreignKeys(): Boolean {
    return remove("foreign_keys")?.let { value ->
        // can be true/false, 1/0, or ON/OFF
        value.toBooleanStrictOrNull()
            ?: value.toIntOrNull()?.let { it == 1 }
            ?: value.equals("ON", ignoreCase = true)
    } ?: false
}

@Suppress("NOTHING_TO_INLINE")
private inline fun MutableMap<String, String>.recursiveTriggers(): Boolean {
    return remove("recursive_triggers")?.let { value ->
        // can be true/false, 1/0, or ON/OFF
        value.toBooleanStrictOrNull()
            ?: value.toIntOrNull()?.let { it == 1 }
            ?: value.equals("ON", ignoreCase = true)
    } ?: false
}
