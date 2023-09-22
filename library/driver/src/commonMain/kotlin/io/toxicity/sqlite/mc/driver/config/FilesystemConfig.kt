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

import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.encryption.EncryptionConfig
import io.toxicity.sqlite.mc.driver.config.encryption.EncryptionMigrationConfig
import io.toxicity.sqlite.mc.driver.internal.ext.appendIndent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * A Config that, when declared, will create the database
 * on the filesystem in the specified [databasesDir] with
 * a file name of [FactoryConfig.Builder.dbName].
 *
 * If omitted from the [FactoryConfig], in memory databases
 * will be created (encryption is not applicable to in memory
 * databases).
 *
 * @see [FactoryConfig.Builder.filesystem]
 * @see [Builder]
 * */
public class FilesystemConfig private constructor(
    @JvmField
    public val databasesDir: DatabasesDir,
    @JvmField
    public val encryptionConfig: EncryptionConfig?,
    @JvmField
    public val encryptionMigrationConfig: EncryptionMigrationConfig?,
) {

    public fun new(
        block: Builder.() -> Unit
    ): FilesystemConfig = Builder(other = this).apply(block).build()

    public companion object {

        /**
         * Helper for creating a new configuration to share
         * across different [SQLiteMCDriver.Factory]s.
         * */
        @JvmStatic
        public fun new(
            databasesDir: DatabasesDir,
            block: Builder.() -> Unit,
        ): FilesystemConfig = Builder(databasesDir).apply(block).build()
    }

    /**
     * Builder for creating a new [FilesystemConfig]
     * */
    @MCConfigDsl
    public class Builder internal constructor(
        @JvmField
        public val databasesDir: DatabasesDir,
    ) {

        internal constructor(other: FilesystemConfig): this(other.databasesDir) {
            encryption(other.encryptionConfig)
            encryptionMigrations(other.encryptionMigrationConfig)
        }

        private var encryptionMigrationConfig: EncryptionMigrationConfig? = null
        private var encryptionConfig: EncryptionConfig? = null

        /**
         * Configure an [EncryptionMigrationConfig] in order to migrate
         * from an old scheme, to the current [EncryptionConfig].
         *
         * **NOTE:** All migrations should be expressed within a
         * single [EncryptionMigrationConfig]. A second invocation
         * of [encryptionMigrations] will replace the currently set
         * [Builder.encryptionMigrationConfig].
         * */
        @MCConfigDsl
        public fun encryptionMigrations(block: EncryptionMigrationConfig.Builder.() -> Unit) {
            encryptionMigrationConfig = EncryptionMigrationConfig
                .Builder(null)
                .apply(block)
                .build()
        }

        /**
         * Set an already existing [EncryptionMigrationConfig], or
         * remove the currently applied config.
         *
         * **NOTE:** All migrations should be expressed within a
         * single [EncryptionMigrationConfig]. A second invocation
         * of [encryptionMigrations] will replace the currently set
         * [Builder.encryptionMigrationConfig].
         *
         * @param [other] inherit from an existing [EncryptionMigrationConfig]
         *   (e.g. for a different database), or remove encryptionMigrations
         *   from the current [FilesystemConfig] by passing null.
         * */
        @MCConfigDsl
        public fun encryptionMigrations(other: EncryptionMigrationConfig?) {
            encryptionMigrationConfig = other
        }

        /**
         * Configure an [EncryptionConfig] to apply to the database file.
         *
         * If an [EncryptionConfig] is omitted from the [FilesystemConfig],
         * the database file will not use encryption.
         * */
        @MCConfigDsl
        public fun encryption(block: EncryptionConfig.Builder.() -> Unit) {
            encryptionConfig = EncryptionConfig.newOrNull(null, block)
        }

        /**
         * Apply an already configured [EncryptionConfig], or remove the
         * currently configured one by passing null.
         *
         * If an [EncryptionConfig] is omitted from the [FilesystemConfig],
         * the database file will not use encryption.
         * */
        @MCConfigDsl
        public fun encryption(other: EncryptionConfig?) {
            encryptionConfig = other
        }

        @JvmSynthetic
        internal fun build(): FilesystemConfig = FilesystemConfig(
            databasesDir = databasesDir,
            encryptionConfig = encryptionConfig,
            encryptionMigrationConfig = encryptionMigrationConfig,
        )
    }

    override fun equals(other: Any?): Boolean {
        return  other is FilesystemConfig
                && other.databasesDir == databasesDir
                && other.encryptionConfig == encryptionConfig
                && other.encryptionMigrationConfig == encryptionMigrationConfig
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 31 + databasesDir.hashCode()
        result = result * 31 + encryptionConfig.hashCode()
        result = result * 31 + encryptionMigrationConfig.hashCode()
        return result
    }

    override fun toString(): String {
        return buildString {
            append(this@FilesystemConfig::class.simpleName)
            appendLine(": [")
            appendIndent("databasesDir: ")
            appendLine(databasesDir.path.toString())

            appendIndent("encryptionConfig: ")
            if (encryptionConfig == null) {
                appendLine("null")
            } else {
                appendLine('[')
                val lines = encryptionConfig.toString().lines()

                for (i in 1..lines.lastIndex) {
                    appendIndent(lines[i])
                    appendLine()
                }
            }

            appendIndent("encryptionMigrationConfig: ")
            if (encryptionMigrationConfig == null) {
                appendLine("null")
            } else {
                appendLine('[')
                val lines = encryptionMigrationConfig.toString().lines()

                for (i in 1..lines.lastIndex) {
                    appendIndent(lines[i])
                    appendLine()
                }
            }

            append(']')
        }
    }
}
