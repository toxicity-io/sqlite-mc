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
package io.toxicity.sqlite.mc.driver.test

import app.cash.sqldelight.db.use
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.FilesystemConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

abstract class SQLiteMCDriverRekeyTest: SQLiteMCDriverTestHelper() {

    @Test
    fun givenDriver_whenInMemory_thenRekeyFails() = runDriverTest(filesystem = null) { factory, driver ->
        driver.close()

        assertFailsWith<IllegalStateException> {
            factory.create(keyPassphrase, keyRaw)
        }
    }

    @Test
    fun givenDriver_whenReKey_thenIsSuccessful() = runBlocking {
        var i = 0
        listOf<Triple<Key, Key, FilesystemConfig.Builder.() -> Unit>>(
            Triple(keyPassphrase, keyRawWithSalt) { encryption { sqlCipher { default() } } },
            Triple(keyPassphrase, keyRawWithSalt) { encryption { sqlCipher { v1() } } },
            Triple(keyPassphrase, keyRawWithSalt) { encryption { sqlCipher { v2() } } },
            Triple(keyPassphrase, keyRawWithSalt) { encryption { sqlCipher { v3() } } },
            Triple(keyPassphrase, keyRawWithSalt) { encryption { sqlCipher { v4() } } },
            Triple(keyPassphrase, keyRawWithSalt) { encryption { chaCha20 { default() } } },
            Triple(keyPassphrase, keyRawWithSalt) { encryption { chaCha20 { sqleet() } } },

            Triple(keyRaw, keyPassphrase) { encryption { sqlCipher { default() } } },
            Triple(keyRaw, keyPassphrase) { encryption { sqlCipher { v1() } } },
            Triple(keyRaw, keyPassphrase) { encryption { sqlCipher { v2() } } },
            Triple(keyRaw, keyPassphrase) { encryption { sqlCipher { v3() } } },
            Triple(keyRaw, keyPassphrase) { encryption { sqlCipher { v4() } } },
            Triple(keyRaw, keyPassphrase) { encryption { chaCha20 { default() } } },
            Triple(keyRaw, keyPassphrase) { encryption { chaCha20 { sqleet() } } },

            Triple(keyRawWithSalt, keyPassphrase) { encryption { sqlCipher { default() } } },
            Triple(keyRawWithSalt, keyPassphrase) { encryption { sqlCipher { v1() } } },
            Triple(keyRawWithSalt, keyPassphrase) { encryption { sqlCipher { v2() } } },
            Triple(keyRawWithSalt, keyPassphrase) { encryption { sqlCipher { v3() } } },
            Triple(keyRawWithSalt, keyPassphrase) { encryption { sqlCipher { v4() } } },
            Triple(keyRawWithSalt, keyPassphrase) { encryption { chaCha20 { default() } } },
            Triple(keyRawWithSalt, keyPassphrase) { encryption { chaCha20 { sqleet() } } },
        ).forEach { (key1, key2, filesystem) ->
            logger("RUN - ${i++}")

            // db files automatically delete once runMCDriverTest completes.
            runDriverTest(key1, filesystem) { factory, driver ->
                val expected = "4314tlkjansd"
                driver.upsert("key", expected)
                assertEquals(expected, driver.get("key"))
                driver.close()

                factory.create(key1, key2).use { driver2 ->
                    assertEquals(expected, driver2.get("key"))
                }

                factory.create(key2).use { driver3 ->
                    assertEquals(expected, driver3.get("key"))
                }
            }
        }
    }

    @Test
    fun givenConfig_whenMigrations_thenRekeyedToNewestEncryptionConfig() = runDriverTest(
        key = keyPassphrase,
        filesystem = { encryption { chaCha20 { sqleet() } } }
    ) { factory1, driver ->
        val expected = "alsdkjnflaskdnfa"
        driver.upsert("key", expected)
        driver.close()

        var migrationAttempts = 0
        fun migrationAttemptsLogger(): (String) -> Unit {
            return { log ->
                if (log.contains("Attempting EncryptionMigration")) {
                    migrationAttempts++
                }

                logger(log)
            }
        }

        // Open DB with chacha20:default (failure)
        // Open DB with chacha20:sqleet (success) << rekey with chacha20:default
        val factory2 = SQLiteMCDriver.Factory(dbName, TestDatabase.Schema) {
            logger = migrationAttemptsLogger()
            redactLogs = false

            filesystem(databasesDir) {
                encryptionMigrations {
                    migrationFrom {
                        note = "Migration from chacha20:sqleet >> chacha20:default"
                        chaCha20 { sqleet() }
                    }
                }

                encryption {
                    chaCha20 { default() }
                }
            }
        }

        // Open DB with sqlcipher:default (failure)
        // Open DB with chacha20:default (should succeed) << rekey with sqlcipher:default
        // Never should make it to chacha20:sqleet b/c factory2 rekeyed
        val factory3 = SQLiteMCDriver.Factory(dbName, TestDatabase.Schema) {
            logger = migrationAttemptsLogger()
            redactLogs = false

            filesystem(databasesDir) {
                encryptionMigrations {
                    migrationFrom {
                        note = "Migration from chacha20:sqleet >> chacha20:default"
                        chaCha20 { sqleet() }
                    }
                    migrationFrom {
                        note = "Migration from chacha20:default >> sqlcipher:default"
                        chaCha20 { default() }
                    }
                }

                encryption {
                    sqlCipher { default() }
                }
            }
        }

        // Open DB with nothing (failure)
        // Open DB with sqlcipher:default (should succeed) << remove encryption
        // Never should make it to chacha20:default b/c factory3 rekeyed
        val factory4 = SQLiteMCDriver.Factory(dbName, TestDatabase.Schema) {
            logger = migrationAttemptsLogger()
            redactLogs = false

            filesystem(databasesDir) {
                encryptionMigrations {
                    migrationFrom {
                        note = "Migration from chacha20:sqleet >> chacha20:default"
                        chaCha20 { sqleet() }
                    }
                    migrationFrom {
                        note = "Migration from chacha20:default >> sqlcipher:default"
                        chaCha20 { default() }
                    }
                    migrationFrom {
                        note = "Migration from sqlcipher:default >> none"
                        sqlCipher { default() }
                    }
                }

                encryption { /* nothing */ }
            }
        }

        factory2.create(keyPassphrase).use { driver2 ->
            assertEquals(expected, driver2.get("key"))
        }
        assertEquals(1, migrationAttempts)

        // Has no migrations expressed, should not affect
        // migrationAttempts, but should fail to open
        assertFailsWith<IllegalStateException> {
            factory1.create(keyPassphrase)
        }

        factory3.create(keyPassphrase).use { driver3 ->
            assertEquals(expected, driver3.get("key"))
        }
        assertEquals(2, migrationAttempts)

        factory3.create(keyPassphrase).close()
        assertEquals(2, migrationAttempts)

        assertFailsWith<IllegalStateException> {
            factory2.create(keyPassphrase)
        }

        factory4.create(keyPassphrase).use { driver4 ->
            assertEquals(expected, driver4.get("key"))
        }
        factory4.create(Key.EMPTY).use { driver4 ->
            assertEquals(expected, driver4.get("key"))
        }
        factory4.create(keyPassphrase).use { driver4 ->
            assertEquals(expected, driver4.get("key"))
        }

        // Empty Key Should also work with factory that
        // has an encryption config defined.
        factory3.create(Key.EMPTY).use { driver3 ->
            assertEquals(expected, driver3.get("key"))
        }
    }

}
