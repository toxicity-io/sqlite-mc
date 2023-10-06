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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import io.toxicity.sqlite.mc.driver.config.*
import io.toxicity.sqlite.mc.driver.config.MutableMCPragmas
import io.toxicity.sqlite.mc.driver.internal.JDBCMCProperties
import io.toxicity.sqlite.mc.driver.internal.JDBCMC
import java.io.File
import java.sql.Connection

public actual sealed class PlatformDriver actual constructor(private val args: Args): JdbcDriver(), SqlDriver {

    private val driver: SqlDriver get() = args.logDriver ?: args.jdbcDriver

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
        args.properties.clear()
    }

    //////////////////////////////////////////////
    // JdbcDriver + ConnectionManager overrides //
    //////////////////////////////////////////////

    final override fun getConnection(): Connection {
        return args.jdbcDriver.getConnection()
    }

    final override fun closeConnection(connection: Connection) {
        args.jdbcDriver.closeConnection(connection)
    }

    final override var transaction: ConnectionManager.Transaction?
        get() = args.jdbcDriver.transaction
        set(value) { args.jdbcDriver.transaction = value }

    final override fun Connection.beginTransaction() {
        with(args.jdbcDriver) { beginTransaction() }
    }

    final override fun Connection.endTransaction() {
        with(args.jdbcDriver) { endTransaction() }
    }

    final override fun Connection.rollbackTransaction() {
        with(args.jdbcDriver) { rollbackTransaction() }
    }

    protected actual companion object {

        @JvmStatic
        @JvmSynthetic
        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal actual fun FactoryConfig.create(keyPragma: MutableMCPragmas, rekeyPragma: MutableMCPragmas?): Args {
            JDBCMC.initialize

            val url = JDBCMC.PREFIX + filesystemConfig.databasesDir.resolve(dbName)
            val properties = JDBCMCProperties.of(keyPragma = keyPragma, rekeyPragma = rekeyPragma)
            properties.putAll(pragmaConfig.filesystem)
            properties.remove("password")

            val driver = try {
                JdbcSqliteDriver(
                    url = url,
                    properties = properties,
                    schema = schema,
                    migrateEmptySchema = platformConfig.migrateEmptySchema,
                    callbacks = afterVersions,
                )
            } catch (t: Throwable) {
                properties.clear()
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to open JDBC connection with $dbName", t)
            }

            return Args(properties, driver, logger?.let { LogSqliteDriver(driver, it) })
        }

        @JvmStatic
        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal actual fun FactoryConfig.create(opt: EphemeralOpt): Args {
            JDBCMC.initialize

            val url = JDBCMC.PREFIX
            val properties = when (opt) {
                EphemeralOpt.IN_MEMORY -> ":memory:"
                EphemeralOpt.NAMED -> "file:$dbName?mode=memory&cache=shared"
                EphemeralOpt.TEMPORARY -> ""
            }.let { JDBCMCProperties.of(it) }
            properties.putAll(pragmaConfig.ephemeral)

            val driver = try {
                JdbcSqliteDriver(
                    url = url,
                    properties = properties,
                    schema = schema,
                    migrateEmptySchema = platformConfig.migrateEmptySchema,
                    callbacks = afterVersions,
                )
            } catch (t: Throwable) {
                if (t is IllegalStateException) throw t
                throw IllegalStateException("Failed to open ephemeral JDBC connection with $dbName", t)
            }

            return Args(properties, driver, logger?.let { LogSqliteDriver(driver, it) })
        }

        internal actual class Args(
            internal val properties: JDBCMCProperties,
            internal val jdbcDriver: JdbcSqliteDriver,
            internal val logDriver: LogSqliteDriver?,
        )
    }
}

@Throws(IllegalStateException::class)
private fun DatabasesDir.resolve(dbName: String): File {
    val directory = path?.let { File(it) } ?: throw IllegalStateException("Failed to resolve DatabasesDir")
    val exists = directory.exists()

    if (exists && !directory.isDirectory) {
        throw IllegalStateException("DatabasesDir exists, but isDirectory[false] (delete???) >> $this")
    }

    if (!exists && !directory.mkdirs()) {
        throw IllegalStateException("Failed to create databases directory >> $this")
    }

    return directory.resolve(dbName)
}
