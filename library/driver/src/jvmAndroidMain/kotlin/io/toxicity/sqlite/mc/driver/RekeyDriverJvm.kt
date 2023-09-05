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
import app.cash.sqldelight.driver.jdbc.ConnectionManager
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import io.toxicity.sqlite.mc.driver.config.*
import io.toxicity.sqlite.mc.driver.config.encryption.EncryptionConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import org.sqlite.JDBC
import java.sql.Connection
import java.sql.SQLException
import java.util.Properties

public actual sealed class RekeyDriver actual constructor(args: Args): JdbcDriver(), SqlDriver {

    private val url: String = args.url
    private val logger: ((String) -> Unit)? = args.logger
    private val properties: Properties = args.properties
    private val jdbcDriver: JdbcSqliteDriver = args.jdbcDriver
    private val logDriver: LogSqliteDriver? = if (logger != null) LogSqliteDriver(jdbcDriver, logger) else null

    private val driver: SqlDriver get() = logDriver ?: jdbcDriver

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected actual fun rekey(key: Key, config: EncryptionConfig) {
        val (pragmas, sqlStatements) = config.createRekeyParameters(
            key = key,
            isInMemory = url == JdbcSqliteDriver.IN_MEMORY
        )

        val (connection, onClose) = try {
            connectionAndClose()
        } catch (e: SQLException) {
            pragmas.clear()
            throw IllegalStateException("Failed to open a connection with JDBC", e)
        }

        try {
            sqlStatements.forEach { sql ->
                val result = connection.prepareStatement(sql).use { jdbcStatement ->
                    JdbcPreparedStatement(jdbcStatement).execute()
                }
                if (result != 0L) throw SQLException()

                // Fake it till we make it
                logger?.invoke("EXECUTE\n $sql")
            }

            // Remove and replace cipher parameters
            with(properties) {
                Pragma.MC.ALL.forEach { remove(it.name) }
                pragmas.forEach { put(it.key.name, it.value) }
            }
        } catch (e: SQLException) {
            throw IllegalStateException("Failed to rekey the database", e)
        } finally {
            pragmas.clear()
            onClose()
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

    //////////////////////////////////////////////
    // JdbcDriver + ConnectionManager overrides //
    //////////////////////////////////////////////

    final override fun getConnection(): Connection {
        return jdbcDriver.getConnection()
    }

    final override fun closeConnection(connection: Connection) {
        jdbcDriver.closeConnection(connection)
    }

    final override var transaction: ConnectionManager.Transaction?
        get() = jdbcDriver.transaction
        set(value) { jdbcDriver.transaction = value }

    final override fun Connection.beginTransaction() {
        with(jdbcDriver) { beginTransaction() }
    }

    final override fun Connection.endTransaction() {
        with(jdbcDriver) { endTransaction() }
    }

    final override fun Connection.rollbackTransaction() {
        with(jdbcDriver) { rollbackTransaction() }
    }

    protected actual companion object {

        @JvmStatic
        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal actual fun FactoryConfig.create(pragmas: Pragmas, isInMemory: Boolean): Args {
            initializeJDBC

            val url = filesystemConfig.toJDBCUrl(dbName, isInMemory)
            val properties = pragmas.toProperties()

            // Always add so that JDBC will apply non-transient MC properties
            // using the sqlite3mc_config interface instead of executing PRAGMA
            // statements.
            properties["mc_use_sql_interface"] = "true"

            val driver = try {
                JdbcSqliteDriver(
                    url = url,
                    properties = properties,
                    schema = schema,
                    migrateEmptySchema = false, // TODO: Move to FactoryConfig.platformOptions
                    callbacks = afterVersions,
                )
            } catch (t: Throwable) {
                properties.clear()
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to open JDBC connection with $dbName", t)
            }

            return Args(url, logger, properties, driver)
        }

        internal actual class Args(
            internal val url: String,
            internal val logger: ((String) -> Unit)?,
            internal val properties: Properties,
            internal val jdbcDriver: JdbcSqliteDriver,
        )
    }
}

@Throws(IllegalStateException::class)
private fun FilesystemConfig?.toJDBCUrl(dbName: String, isInMemory: Boolean): String {
    // TODO: Resource database
    if (this == null || isInMemory) return JdbcSqliteDriver.IN_MEMORY

    databasesDir.ensureExists()

    return JdbcSqliteDriver.IN_MEMORY + databasesDir.directory.resolve(dbName)
}

private fun Pragmas.toProperties(): Properties {
    val properties = Properties()
    forEach { entry -> properties[entry.key.name] = entry.value }
    return properties
}

@Throws(IllegalStateException::class)
private fun DatabasesDir.ensureExists() {
    val exists = directory.exists()

    if (exists && !directory.isDirectory) {
        throw IllegalStateException("DatabasesDir exists, but isDirectory[false] (delete???) >> $this")
    }

    if (!exists && !directory.mkdirs()) {
        throw IllegalStateException("Failed to create databases directory >> $this")
    }
}

// Service loaders do not work properly on Android API 23
// and below. Referencing JDBC will cause it to automatically
// register itself with DriverManager.
//
// https://github.com/cashapp/sqldelight/issues/4575
private val initializeJDBC: Unit by lazy {
    JDBC.isValidURL(null)
    Unit
}
