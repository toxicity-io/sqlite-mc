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
import io.toxicity.sqlite.mc.driver.test.helper.TestHelperNonEphemeral
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

abstract class RekeyTest: TestHelperNonEphemeral() {

    @Test
    open fun givenDriver_whenReKey_thenIsSuccessful() = runBlocking {
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
            testLogger("RUN - ${i++}")

            // db files automatically delete once runMCDriverTest completes.
            runDriverTest(key1, filesystem = filesystem, timeout = 20.seconds) { factory, driver ->
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
    open fun givenConfig_whenMigrations_thenRekeyedToNewestEncryptionConfig() = runDriverTest(
        key = keyPassphrase,
        filesystem = { encryption { chaCha20 { sqleet() } } },
        timeout = 25.seconds,
    ) { factory1, driver ->
        val dbName = factory1.config.dbName

        val expected = "alsdkjnflaskdnfa"
        driver.upsert("key", expected)
        driver.close()

        var migrationAttempts = 0
        fun migrationAttemptsLogger(): (String) -> Unit {
            return { log ->
                if (log.contains("Attempting EncryptionMigration")) {
                    migrationAttempts++
                }

                testLogger(log)
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

        // Factory 3 has already successfully opened.
        // Bad password should not attempt any migrations
        assertFailsWith<IllegalStateException> {
            factory3.create(keyRawWithSalt)
        }
        assertEquals(2, migrationAttempts)

        // Should open with correct password
        factory3.create(keyPassphrase).close()

        // Should fail, factory2 uses an old encryption scheme
        assertFailsWith<IllegalStateException> {
            factory2.create(keyPassphrase)
        }

        // Should succeed first try w/o migration attempts
        factory3.create(keyPassphrase).use { driver3 ->
            assertEquals(expected, driver3.get("key"))
        }

        // Any further openings using factory 2 or 3 should not
        // have attempted any migrations, as there was already
        // a successful first open for that factory.
        assertEquals(2, migrationAttempts)
    }

}
