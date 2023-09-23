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
import io.toxicity.sqlite.mc.driver.config.Pragmas
import io.toxicity.sqlite.mc.driver.config.encryption.Cipher
import io.toxicity.sqlite.mc.driver.config.encryption.EncryptionConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import io.toxicity.sqlite.mc.driver.config.mutablePragmas
import io.toxicity.sqlite.mc.driver.internal.ext.buildMCConfigSQL
import io.toxicity.sqlite.mc.driver.internal.ext.createRekeyParameters

public actual sealed class PlatformDriver actual constructor(args: Args): SqlDriver {

    private val isInMemory: Boolean = args.isInMemory
    private val logger: ((String) -> Unit)? = args.logger
    private val properties: MutablePragmas = args.properties
    private val nativeDriver: NativeSqliteDriver = args.nativeDriver
    private val dbManager: DatabaseManager = args.dbManager
    private val logDriver: LogSqliteDriver? = if (logger != null) LogSqliteDriver(nativeDriver, logger) else null

    private val driver: SqlDriver get() = logDriver ?: nativeDriver

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected actual fun rekey(key: Key, config: EncryptionConfig) {
        val (pragmas, sqlStatements) = config.createRekeyParameters(key = key, isInMemory = isInMemory)

        val connection = dbManager.createSingleThreadedConnection()

        try {
            sqlStatements.forEach { sql ->
                logger?.invoke("EXECUTE\n $sql")
                connection.rawExecSql(sql)
            }

            // Remove and replace cipher parameters
            properties.removeMCPragmas()
            properties.putAll(pragmas)
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to rekey the database", t)
        } finally {
            pragmas.clear()
            connection.close()
        }
    }

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
        properties.clear()
        driver.close()
    }
    
    protected actual companion object {
        
        @Throws(IllegalStateException::class)
        internal actual fun FactoryConfig.create(pragmas: Pragmas, isInMemory: Boolean): Args {

            // TODO: Need to make access concurrent
            val properties = mutablePragmas()
            properties.putAll(pragmas)

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
                inMemory = isInMemory,
                journalMode = JournalMode.WAL, // TODO: Move to FactoryConfig.platformOptions
                extendedConfig = DatabaseConfiguration.Extended( // TODO: Move to FactoryConfig.platformOptions
                    foreignKeyConstraints = false,
                    busyTimeout = 5_000,
                    pageSize = null,
                    synchronousFlag = null,
                    basePath = filesystemConfig?.databasesDir?.path,
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
                        var cipher: Cipher? = null

                        val sqlStatements = Pragma.MC.ALL.mapNotNull { pragma ->
                            val value = properties[pragma] ?: return@mapNotNull null

                            when (pragma) {
                                is Pragma.MC.CIPHER -> {
                                    cipher = Cipher.valueOfOrNull(value) ?: return@mapNotNull null

                                    pragma.name.buildMCConfigSQL(
                                        transient = false,
                                        arg2 = value,
                                        arg3 = null
                                    )
                                }
                                is Pragma.MC.KEY -> {
                                    "PRAGMA ${pragma.name} = $value;"
                                }
                                else -> {
                                    val arg3: Number = value.toIntOrNull() ?: return@mapNotNull null

                                    cipher?.name?.buildMCConfigSQL(
                                        transient = false,
                                        arg2 = pragma.name,
                                        arg3 = arg3,
                                    )
                                }
                            }
                        }

                        if (cipher != null) {
                            logger?.invoke("onCreateConnection - START")

                            for (statement in sqlStatements) {
                                logger?.invoke("EXECUTE\n $statement")
                                conn.rawExecSql(statement)
                            }

                            logger?.invoke("EXECUTE\n SELECT 1 FROM sqlite_schema;")
                            conn.stringForQuery("SELECT 1 FROM sqlite_schema;")

                            logger?.invoke("onCreateConnection - FINISH")
                        }
                    },
                    onCloseConnection = {  },
                ),
                encryptionConfig = DatabaseConfiguration.Encryption(null, null)
            )

            val manager: DatabaseManager = try {
                createDatabaseManager(config)
            } catch (t: Throwable) {
                properties.clear()
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to create DatabaseManager", t)
            }

            val driver = NativeSqliteDriver(manager, maxReaderConnections = 1) // TODO: Move to FactoryConfig.platformOptions

            // TODO: need to rework creating an in memory db, as you could create an in memory
            //  database for a file that is encrypted, which would need to be initialized
            //  here to check for key correctness.
            if (!isInMemory) {
                try {
                    driver.executeQuery(
                        null,
                        "SELECT 1 FROM sqlite_schema;",
                        mapper = { QueryResult.Unit },
                        parameters = 0,
                        binders = null
                    )
                } catch (t: Throwable) {
                    properties.clear()
                    driver.close()
                    if (t is IllegalStateException) throw t
                    throw IllegalStateException("Failed to create NativeSqliteDriver", t)
                }
            }

            return Args(
                isInMemory = isInMemory,
                logger = logger,
                properties = properties,
                nativeDriver = driver,
                dbManager = manager,
            )
        }

        internal actual class Args(
            internal val isInMemory: Boolean,
            internal val logger: ((String) -> Unit)?,
            internal val properties: MutablePragmas,
            internal val nativeDriver: NativeSqliteDriver,
            internal val dbManager: DatabaseManager,
        )
    }
}
