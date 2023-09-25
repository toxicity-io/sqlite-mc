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
import io.toxicity.sqlite.mc.driver.config.encryption.*
import io.toxicity.sqlite.mc.driver.config.mutablePragmas
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
         * @param [key] The encryption [Key] to decrypt the database with.
         * @see [Key]
         * @see [createBlocking]
         * @throws [IllegalArgumentException] if [key] is inappropriate
         *   for the configured [MCCipherConfig] (see [Key.raw] for
         *   more details).
         * @throws [IllegalStateException] if the database failed
         *   to open (e.g. an incorrect [Key]).
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public suspend fun create(key: Key): SQLiteMCDriver = createActual(key, null)

        /**
         * Creates a new [SQLiteMCDriver] for the given [config] and changes
         * the encryption [Key] to [rekey].
         *
         * Encryption can be removed by passing [Key.Empty] for [rekey].
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
        public suspend fun create(key: Key, rekey: Key): SQLiteMCDriver = createActual(key, rekey)

        /**
         * Creates an ephemeral [SQLiteMCDriver] for the given [config] and
         * [EphemeralOpt].
         *
         * @param [opt] The type of ephemeral database to create.
         * @see [EphemeralOpt]
         * @throws [IllegalStateException] if a connection failed to be
         *   created.
         * */
        @Throws(IllegalStateException::class, CancellationException::class)
        public suspend fun create(opt: EphemeralOpt): SQLiteMCDriver {
            return config.withDispatcher { SQLiteMCDriver(config, create(opt)) }
        }

        /**
         * Blocking call for [create]
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public fun createBlocking(key: Key): SQLiteMCDriver = runBlocking { create(key) }

        /**
         * Blocking call for [create]
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public fun createBlocking(key: Key, rekey: Key): SQLiteMCDriver = runBlocking { create(key, rekey) }

        /**
         * Blocking call for [create]
         * */
        @Throws(
            IllegalArgumentException::class,
            IllegalStateException::class,
            CancellationException::class,
        )
        public fun createBlocking(opt: EphemeralOpt): SQLiteMCDriver = runBlocking { create(opt) }

        private suspend fun createActual(key: Key, rekey: Key?): SQLiteMCDriver {
            return config.withDispatcher {

                val args = try {
                    val keyPragma = mutablePragmas().apply {
                        filesystemConfig.encryptionConfig
                            .applyPragmas(this)
                            .applyKeyPragma(this, key, isRekey = false)
                    }

                    val rekeyPragma = if (rekey != null) {
                        mutablePragmas().apply {
                            filesystemConfig.encryptionConfig
                                .applyPragmas(this)
                                .applyKeyPragma(this, rekey, isRekey = true)
                        }
                    } else {
                        null
                    }

                    create(keyPragma, rekeyPragma)
                } catch (e: IllegalStateException) {

                    val migrations = filesystemConfig.encryptionMigrationConfig
                    if (migrations != null && !hasOpened) {
                        // if rekey is null, use same key when migrating from old
                        // migration encryption config, to current encryption config.
                        migrations.attemptUpgrade(key = key, rekey = rekey ?: key)
                    } else {
                        throw e
                    }
                }

                val driver = SQLiteMCDriver(config, args)

                hasOpened = true
                driver
            }
        }

        @Throws(IllegalStateException::class)
        private fun EncryptionMigrationConfig.attemptUpgrade(key: Key, rekey: Key): Companion.Args {
            var lastError: Throwable? = null

            var i = migrations.size
            for (migration in migrations.reversed()) {

                try {
                    val cipher = migration.encryptionConfig.cipherConfig.cipher
                    val legacy = migration.encryptionConfig.cipherConfig.legacy

                    config.logger?.invoke("Attempting EncryptionMigration (${i--}) - $cipher: legacy = $legacy")

                    val migrationPragma = mutablePragmas().apply {
                        migration.encryptionConfig
                            .applyPragmas(this)
                            .applyKeyPragma(this, key, isRekey = false)
                    }

                    val currentPragma = mutablePragmas().apply {
                        config.filesystemConfig.encryptionConfig
                            .applyPragmas(this)
                            .applyKeyPragma(this, rekey, isRekey = true)
                    }

                    val args = config.create(keyPragma = migrationPragma, rekeyPragma = currentPragma)

                    val cCipher = config.filesystemConfig.encryptionConfig.cipherConfig.cipher
                    val cLegacy = config.filesystemConfig.encryptionConfig.cipherConfig.legacy
                    config.logger?.invoke("Successful rekey to $cCipher: legacy = $cLegacy")

                    return args
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            throw IllegalStateException("Failed to open database. All migration attempts failed.", lastError)
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
