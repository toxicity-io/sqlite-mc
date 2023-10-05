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
    public val filesystemConfig: FilesystemConfig,
    @JvmField
    public val platformConfig: PlatformConfig,
    @JvmField
    public val pragmaConfig: PragmaConfig,

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
            require(!dbName.contains('?')) {
                "Invalid dbName. Cannot contain question marks '?'"
            }
        }

        private var filesystemConfig: FilesystemConfig? = null
        private var platformConfig: PlatformConfig? = null
        private var pragmaConfig: PragmaConfig? = null

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
            of: Long,
            block: (SqlDriver) -> Unit,
        ): Builder {
            afterVersions.add(AfterVersion(afterVersion = of, block))
            return this
        }

        /**
         * Create a new [PlatformConfig], or updates the currently set
         * [platformConfig].
         * */
        @MCConfigDsl
        public fun platform(
            block: PlatformConfig.Builder.() -> Unit,
        ): Builder {
            platformConfig = PlatformConfig.new(platformConfig, block)
            return this
        }

        /**
         * Set an already existing [PlatformConfig], or remove the currently
         * applied config by passing null.
         * */
        @MCConfigDsl
        public fun platform(
            other: PlatformConfig?
        ): Builder {
            platformConfig = other
            return this
        }

        /**
         * Create a [PragmaConfig], or updates the currently set
         * [pragmaConfig].
         * */
        @MCConfigDsl
        public fun pragmas(
            block: PragmaConfig.Builder.() -> Unit,
        ): Builder {
            pragmaConfig = PragmaConfig.new(pragmaConfig, block)
            return this
        }

        /**
         * Set an already existing [PragmaConfig], or remove the currently
         * applied config by passing null.
         * */
        @MCConfigDsl
        public fun pragmas(
            other: PragmaConfig?,
        ): Builder {
            pragmaConfig = other
            return this
        }

        /**
         * Create a new [FilesystemConfig].
         *
         * If [FilesystemConfig] is not configured, [FilesystemConfig.Default]
         * will be used.
         *
         * @see [FilesystemConfig.Default]
         * */
        @MCConfigDsl
        @JvmOverloads
        public fun filesystem(
            databasesDir: DatabasesDir,
            block: FilesystemConfig.Builder.() -> Unit = {},
        ): Builder {
            filesystemConfig = FilesystemConfig.new(databasesDir, block)
            return this
        }

        /**
         * Inherit all settings from another [FilesystemConfig] and
         * (optionally) modify it further.
         *
         * If [FilesystemConfig] is not configured, [FilesystemConfig.Default]
         * will be used.
         *
         * @see [FilesystemConfig.new]
         * @see [FilesystemConfig.Default]
         * */
        @MCConfigDsl
        @JvmOverloads
        public fun filesystem(
            other: FilesystemConfig,
            block: FilesystemConfig.Builder.() -> Unit = {},
        ): Builder {
            filesystemConfig = other.new(block)
            return this
        }

        @JvmSynthetic
        internal fun build(): FactoryConfig = FactoryConfig(
            dbName = dbName,
            schema = schema,
            filesystemConfig = filesystemConfig ?: FilesystemConfig.Default,
            platformConfig = platformConfig ?: PlatformConfig.Default,
            pragmaConfig = pragmaConfig ?: PragmaConfig.Default,
            dispatcher = dispatcher.let { dispatcher ->
                if (dispatcher == Dispatchers.IO) {
                    @OptIn(ExperimentalCoroutinesApi::class)
                    dispatcher.limitedParallelism(parallelism = 1)
                } else {
                    dispatcher
                }
            },
            afterVersions = afterVersions.toTypedArray(),
            logger = logger.toRedactedLoggerOrNull(redactLogs, dbName),
        )
    }
}

private fun ((String) -> Unit)?.toRedactedLoggerOrNull(redactLogs: Boolean, dbName: String): ((String) -> Unit)? {
    val logger = this ?: return null

    return { log ->
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
}
