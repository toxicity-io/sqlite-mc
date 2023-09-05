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
package io.toxicity.sqlite.mc.driver.config

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.logs.LogSqliteDriver
import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic

/**
 * A Config for [SQLiteMCDriver.Factory].
 *
 * @see [Builder]
 * */
public class FactoryConfig private constructor(
    @JvmField
    public val dbName: String,
    @JvmField
    public val schema: SqlSchema<QueryResult.Value<Unit>>,
    @JvmField
    public val filesystemConfig: FilesystemConfig?,

    @JvmSynthetic
    internal val dispatcher: CoroutineDispatcher,
    @JvmSynthetic
    internal val afterVersions: Array<AfterVersion>,
    @JvmSynthetic
    internal val logger: ((String) -> Unit)?,
) {

    /**
     * Primary entry point for creating a new [FactoryConfig].
     *
     * @see [SQLiteMCDriver.Factory]
     * */
    @MCConfigDsl
    public class Builder
    @Throws(IllegalArgumentException::class)
    internal constructor(
        @JvmField
        public val dbName: String,
        @JvmField
        public val schema: SqlSchema<QueryResult.Value<Unit>>,
    ) {

        init {
            require(dbName.isNotBlank()) {
                "Invalid dbName. Cannot be blank."
            }
            require(dbName.lines().size == 1) {
                "Invalid dbName. Cannot be multiple lines."
            }
            require(!dbName.contains('/') && !dbName.contains('\\')) {
                "Invalid dbName. Cannot contain file system separator characters / or \\"
            }
        }

        private var filesystemConfig: FilesystemConfig? = null

        // internal for testing only
        @JvmField
        @JvmSynthetic
        internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

        /**
         * Will wrap the platform specific driver in [LogSqliteDriver]
         * before creating an [SQLiteMCDriver]. All [SQLiteMCDriver]s for the given
         * [FactoryConfig] will utilize this [logger].
         *
         * PRAGMA key and PRAGMA rekey statements will be redacted
         * before the log is passed to [logger].
         * */
        @JvmField
        public var logger: ((String) -> Unit)? = null

        /**
         * Disable [logger] log redaction of PRAGMA key + PRAGMA rekey events
         *
         * **NOTE:** Do not disable in production environments if using [logger].
         * */
        @JvmField
        public var redactLogs: Boolean = true

        @JvmField
        public val afterVersions: MutableList<AfterVersion> = mutableListOf()

        @MCConfigDsl
        public fun afterVersion(
            value: Long,
            block: (SqlDriver) -> Unit,
        ) {
            afterVersions.add(AfterVersion(value, block))
        }

        /**
         * Create a new [FilesystemConfig].
         *
         * If a [FilesystemConfig] is omitted, calls to
         * [SQLiteMCDriver.Factory.create] will generate in memory databases.
         * */
        @MCConfigDsl
        @JvmOverloads
        public fun filesystem(
            databasesDir: DatabasesDir,
            block: FilesystemConfig.Builder.() -> Unit = {},
        ) {
            filesystemConfig = FilesystemConfig.new(databasesDir, block)
        }

        /**
         * Inherit all settings from another [FilesystemConfig] and
         * (optionally) modify it further.
         *
         * If a [FilesystemConfig] is omitted, calls to
         * [SQLiteMCDriver.Factory.create] will generate in memory databases.
         *
         * @see [FilesystemConfig.new]
         * */
        @MCConfigDsl
        @JvmOverloads
        public fun filesystem(
            other: FilesystemConfig,
            block: FilesystemConfig.Builder.() -> Unit = {},
        ) {
            filesystemConfig = other.new(block)
        }

        @JvmSynthetic
        internal fun build(): FactoryConfig {
            val dispatcher = dispatcher
            val logger = logger
            val redactLogs = redactLogs

            return FactoryConfig(
                dbName = dbName,
                schema = schema,
                filesystemConfig = filesystemConfig,
                dispatcher = if (dispatcher == Dispatchers.IO) {
                    @OptIn(ExperimentalCoroutinesApi::class)
                    dispatcher.limitedParallelism(parallelism = 1)
                } else {
                    dispatcher
                },
                afterVersions = afterVersions.toTypedArray(),
                logger = if (logger != null) {
                    { log ->
                        val cleansed = if(
                            redactLogs
                            && log.startsWith("EXECUTE\n PRAGMA", ignoreCase = true)
                            && (
                                   log.contains("${Pragma.MC.RE_KEY.name} =", ignoreCase = true)
                                   || log.contains("${Pragma.MC.KEY.name} =", ignoreCase = true)
                               )
                        ) {
                            log.replaceAfter("=", "") + " [REDACTED];"
                        } else {
                            log
                        }

                        try {
                            logger("$dbName: $cleansed")
                        } catch (_: Throwable) {}
                    }
                } else {
                    null
                },
            )
        }
    }
}
