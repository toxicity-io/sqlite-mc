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
package io.toxicity.sqlite.mc.driver.config.encryption

import io.matthewnelson.immutable.collections.toImmutableSet
import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.FilesystemConfig
import io.toxicity.sqlite.mc.driver.internal.ext.appendIndent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * A Config that, when declared, will allow for migration
 * to a new [EncryptionConfig] declaration.
 *
 * When an application wants to change its currently set
 * [EncryptionConfig], it can do so by moving its
 * currently declared [FilesystemConfig.Builder.encryption]
 * block to a [EncryptionMigrationConfig.Builder.migrationFrom]
 * block and declare a new [FilesystemConfig.Builder.encryption]
 * block.
 *
 * The next attempt to open the database will fail (the
 * encryption was changed). As such, all migrations that
 * are expressed will be attempted in descending order
 * until opening is successful. At that time, a rekey
 * will occur to apply the new [EncryptionConfig].
 *
 * e.g.
 *
 *    // Before:
 *    filesystem {
 *        encryption {
 *            sqlCipher { v3() }
 *        }
 *    }
 *
 *    // After:
 *    filesystem {
 *        encryptionMigrations {
 *
 *            // Note to self: never remove or modify
 *            migrationFrom {
 *                note = """
 *                    Migrating away from SQLCipher library
 *                """.trimIndent()
 *
 *                sqlCipher { v3() }
 *            }
 *        }
 *
 *        encryption {
 *            chaCha20 {
 *                default {
 *                    kdfIter(200_000)
 *                }
 *            }
 *        }
 *    }
 *
 *    // Another Migration 1.5 years later:
 *    filesystem {
 *        encryptionMigrations {
 *
 *            // Note to self: never remove or modify
 *            migrationFrom {
 *                note = """
 *                    Migrating away from SQLCipher library
 *                """.trimIndent()
 *
 *                sqlCipher { v3() }
 *            }
 *
 *            // Note to self: never remove or modify
 *            migrationFrom {
 *                note = """
 *                    Migrating back to SQLCipher encryption
 *                    for reasons
 *                """.trimIndent()
 *
 *                chaCha20 {
 *                    default {
 *                        kdfIter(200_000)
 *                    }
 *                }
 *            }
 *        }
 *
 *        encryption {
 *            sqlCipher {
 *                default {
 *                    kdfIter(500_000)
 *                }
 *            }
 *        }
 *    }
 *
 * In the last example, an attempt to open the database using SQLCipher with
 * 500_000 kdf iterations will be made. On failure, ChaCha20 with 200_000 kdf
 * iterations will be attempted. On failure, SQLCipher legacy v3 will be
 * attempted. Once it opens, the new configuration of SQLCipher with 500_000
 * kdf iterations will be applied using the provided [Key].
 *
 * It is note worth to touch on [Key] types, too. SQLCipher and ChaCha20 both
 * support usage of [Key.raw], so migrations between either of those
 * [MCCipherConfig] types will work when using a raw key. As such, care
 * and consideration must be had with regard to the [Key] type when you
 * are releasing an update that contains a migration.
 *
 * Once a migration is declared, removal or modification of it should
 * be done with great consideration to your users. If a user opens your
 * application for the first time in 2 year and the migration
 * declaration was removed or modified, will they be able to open the
 * database?
 *
 * @see [Builder]
 * */
public class EncryptionMigrationConfig private constructor(
    @JvmField
    public val migrations: Set<EncryptionMigration>
) {

    /**
     * Create a new configuration based off of this one.
     *
     * @throws [IllegalStateException] if no migrations were specified
     * */
    @Throws(IllegalStateException::class)
    public fun new(
        block: Builder.() -> Unit
    ): EncryptionMigrationConfig = newOrNull(block)
        ?: throw IllegalStateException("No migrations were configured")

    /**
     * Create a new configuration based off of this one.
     * */
    public fun newOrNull(
        block: Builder.() -> Unit
    ): EncryptionMigrationConfig? = newOrNull(this, block)

    public companion object {

        /**
         * Helper for creating a new configuration to share
         * across different [SQLiteMCDriver.Factory] or [FilesystemConfig].
         *
         * @param [other] another configuration to inherit from
         * @throws [IllegalStateException] if no migrations were specified
         * */
        @JvmStatic
        @Throws(IllegalStateException::class)
        public fun new(
            other: EncryptionMigrationConfig?,
            block: Builder.() -> Unit
        ): EncryptionMigrationConfig = newOrNull(other, block)
            ?: throw IllegalStateException("No migrations were configured")

        /**
         * Helper for creating a new configuration to share
         * across different [SQLiteMCDriver.Factory] or [FilesystemConfig].
         *
         * @param [other] another configuration to inherit from
         * */
        @JvmStatic
        public fun newOrNull(
            other: EncryptionMigrationConfig?,
            block: Builder.() -> Unit,
        ): EncryptionMigrationConfig? = Builder(other).apply(block).build()
    }

    @MCConfigDsl
    public class Builder internal constructor(other: EncryptionMigrationConfig?) {

        // TODO: Migration Completion Handler???

        private val migrations = mutableSetOf<EncryptionMigration>()

        /**
         * Add a *single* [EncryptionConfig] to the migration.
         *
         * e.g. Incorrect:
         *
         *     migrationFrom {
         *         sqlCipher { v1() } // will **NOT** be added
         *         chaCha20 { default() } // **WILL** be added
         *     }
         *
         * e.g. Correct:
         *
         *     migrationFrom {
         *         sqlCipher { v1() }
         *     }
         *     migrationFrom {
         *         chaCha20 { default() }
         *     }
         * */
        @MCConfigDsl
        public fun migrationFrom(
            block: EncryptionMigration.Builder.() -> Unit
        ): Builder {
            val migration = EncryptionMigration.Builder().apply(block).buildMigration()
            if (migration != null) {
                migrations.add(migration)
            }
            return this
        }

        /**
         * Add an already configured [EncryptionMigration]
         * */
        @MCConfigDsl
        public fun migrationFrom(
            other: EncryptionMigration
        ): Builder {
            migrations.add(other)
            return this
        }

        init {
            if (other != null) {
                migrations.addAll(other.migrations)
            }
        }

        @JvmSynthetic
        internal fun build(): EncryptionMigrationConfig? {
            val set = migrations.toImmutableSet()
            if (set.isEmpty()) return null
            return EncryptionMigrationConfig(set)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is EncryptionMigrationConfig && other.migrations == migrations
    }

    override fun hashCode(): Int {
        var result = 17
        migrations.forEach { migration ->
            result = result * 31 + migration.hashCode()
        }
        return result
    }

    override fun toString(): String {
        return buildString {
            append(this@EncryptionMigrationConfig::class.simpleName)
            appendLine(": [")

            var i = 1
            migrations.forEach { migration ->
                appendIndent("migration (")
                append(i++)
                appendLine("): [")

                val lines = migration.toString().lines()

                for (j in 1..lines.lastIndex) {
                    appendIndent(lines[j])
                    appendLine()
                }
            }
            append(']')
        }
    }
}
