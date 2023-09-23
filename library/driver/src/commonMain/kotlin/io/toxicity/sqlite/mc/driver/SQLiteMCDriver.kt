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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import io.toxicity.sqlite.mc.driver.config.*
import io.toxicity.sqlite.mc.driver.config.MutablePragmas
import io.toxicity.sqlite.mc.driver.config.encryption.*
import io.toxicity.sqlite.mc.driver.config.mutablePragmas
import io.toxicity.sqlite.mc.driver.config.removeMCPragmas
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField

/**
 * SQLDelight Driver backed by SQLite3MultipleCiphers for
 * database file encryption.
 *
 * See: [SQLite3MultipleCiphers](https://github.com/utelle/SQLite3MultipleCiphers)
 *
 * @see [Factory]
 * @see [MCConfigQueries]
 * */
public class SQLiteMCDriver private constructor(
    @JvmField
    public val config: FactoryConfig,
    @JvmField
    public val isInMemory: Boolean,
    args: Companion.Args
): PlatformDriver(args) {

    /**
     * Primary entry point to create new [SQLiteMCDriver] instances.
     *
     * TLDR; 1 [Factory] should be defined for each database file
     * that is to be created. This allows for managing the encryption
     * layer of that database file **much** simpler. It enables
     * different encryption scheme declarations, encryption migrations,
     * password management etc., specific to that database file.
     *
     * @see [create]
     * @see [create]
     * */
    public class Factory private constructor(
        @JvmField
        public val config: FactoryConfig,
    ) {

        // TODO: Volatile
        private var hasOpened = false

        /**
         * Create a new [Factory] configuration for the given
         * [dbName] and [schema].
         *
         * @param [dbName] The name of the database (e.g. test.db)
         * @param [schema] The Database Schema
         * @param [block] Lambda for applying a [FactoryConfig]
         * @throws [IllegalArgumentException] if [dbName] is inappropriate
         * */
        @Throws(IllegalArgumentException::class)
        public constructor(
            dbName: String,
            schema: SqlSchema<QueryResult.Value<Unit>>,
            block: FactoryConfig.Builder.() -> Unit,
        ): this(FactoryConfig.Builder(dbName, schema).apply(block).build())

        /**
         * Creates a new [SQLiteMCDriver] for the given [config].
         *
         * @param [key] The encryption [Key] to decrypt the database
         *   with. If null, an in memory database will be opened.
         * @see [Key]
         * @see [createBlocking]
         * @throws [IllegalArgumentException] if [key] is inappropriate
         *   for the configured [MCCipherConfig] (see [Key.raw] for
         *   more details).
         * @throws [IllegalStateException] if the database failed
         *   to open (e.g. an incorrect [Key.passphrase]).
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public suspend fun create(key: Key?): SQLiteMCDriver {
            val isInMemory = key == null || config.filesystemConfig == null

            return config.withDispatcher {

                val pragmas = mutablePragmas()

                if (key != null) {
                    filesystemConfig?.encryptionConfig
                        ?.applyPragmas(pragmas)
                        ?.applyKeyPragma(pragmas, key)
                }

                // will be null if there is no migration
                var migrationKey: Key? = null

                val args = try {
                    create(pragmas, isInMemory)
                } catch (e: IllegalStateException) {

                    val migrations = filesystemConfig?.encryptionMigrationConfig
                    if (key != null && migrations != null && !hasOpened) {
                        migrationKey = key
                        migrations.attemptOpening(key, pragmas)
                    } else {
                        throw e
                    }
                } finally {
                    pragmas.removeMCPragmas()
                }

                val driver = SQLiteMCDriver(config, isInMemory, args)

                if (migrationKey != null) {
                    driver.rekey(migrationKey)
                }

                hasOpened = true
                driver
            }
        }

        /**
         * Creates a new [SQLiteMCDriver] for the given [config] and changes
         * the encryption [Key] to [rekey].
         *
         * Encryption can be removed by passing [Key.EMPTY] for [rekey].
         *
         * @param [key] The encryption [Key] to decrypt the database with.
         * @param [rekey] The encryption [Key] to rekey the database to.
         * @see [Key]
         * @see [createBlocking]
         * @throws [IllegalArgumentException] if [key] is inappropriate
         *   for the configured [MCCipherConfig] (see [Key.raw] for
         *   more details).
         * @throws [IllegalStateException] if the database failed
         *   to open (e.g. an incorrect [Key.passphrase]).
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public suspend fun create(key: Key, rekey: Key): SQLiteMCDriver {
            val driver = create(key)
            driver.rekey(rekey)
            return driver
        }

        /**
         * Blocking call for [create]
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public fun createBlocking(key: Key?): SQLiteMCDriver = runBlocking { create(key) }

        /**
         * Blocking call for [create]
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public fun createBlocking(key: Key, rekey: Key): SQLiteMCDriver = runBlocking { create(key, rekey) }

        @Throws(IllegalStateException::class)
        private fun EncryptionMigrationConfig.attemptOpening(
            key: Key,
            pragmas: MutablePragmas,
        ): Companion.Args {
            var lastError: Throwable? = null

            var i = migrations.size
            for (migration in migrations.reversed()) {
                pragmas.removeMCPragmas()

                try {
                    val cipher = migration.encryptionConfig.cipherConfig.cipher
                    val legacy = migration.encryptionConfig.cipherConfig.legacy

                    config.logger?.invoke("Attempting EncryptionMigration (${i--}) - $cipher: legacy = $legacy")

                    migration.encryptionConfig
                        .applyPragmas(pragmas)
                        .applyKeyPragma(pragmas, key)

                    val args = config.create(pragmas, isInMemory = false)

                    config.logger?.invoke("Successful open. Performing a rekey to new EncryptionConfig")
                    return args
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            throw IllegalStateException("Failed to open database. All migrations failed.", lastError)
        }
    }

    @Throws(
        IllegalArgumentException::class,
        IllegalArgumentException::class,
        CancellationException::class,
    )
    private suspend fun rekey(key: Key) {
        var rekey = key

        val encryptionConfig = config
            .filesystemConfig
            ?.encryptionConfig
            ?: EncryptionConfig.new(null) {
                // Config was null, need to select anything and rekey
                // to empty key value in order to remove encryption
                config.logger?.invoke("No EncryptionConfig. Removing encryption.")
                rekey = Key.EMPTY
                chaCha20(MCChaCha20Config.Default)
            }

        config.withDispatcher {
            rekey(rekey, encryptionConfig)
        }
    }
}

@OptIn(ExperimentalContracts::class)
private suspend inline fun <R: Any?> FactoryConfig.withDispatcher(
    crossinline block: suspend FactoryConfig.() -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return withContext(dispatcher) { block() }
}
