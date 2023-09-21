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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.test.*

abstract class SQLiteMCDriverTest: SQLiteMCDriverTestHelper() {

    @Test
    fun givenDriver_whenOpened_thenIsSuccessful() = runDriverTest { _, driver ->
        val expected = "alskdjvn"
        driver.upsert("key", expected)
        assertEquals(expected, driver.get("key"))
    }

    @Test
    fun givenDriver_whenReOpened_thenIsSuccessful() = runDriverTest { factory, driver ->
        val expected = "abcd123"
        driver.upsert("key", expected)

        driver.close()

        factory.create(keyPassphrase).use { driver2 ->
            assertEquals(expected, driver2.get("key"))
        }
    }

    @Test
    fun givenDriver_whenIncorrectPassword_thenOpenFails() = runDriverTest { factory, driver ->
        driver.upsert("key", "asdfasdf")
        driver.close()

        assertFailsWith<IllegalStateException> {
            factory.create(Key.passphrase("incorrect"))
        }
    }

    @Test
    fun givenDriver_whenNoFilesystem_thenCreatesInMemory() = runDriverTest(filesystem = null) { factory, driver ->
        val expected = "abcd123"
        driver.upsert("key", expected)
        driver.close()

        // Even if passed a key, the PRAGMA key property should not be applied
        factory.create(keyPassphrase).use { driver2 ->
            assertNotEquals(expected, driver2.get("key"))
        }
    }

    @Test
    fun givenDriver_whenFilesystemButNullKey_thenCreatesInMemory() = runDriverTest { factory, driver ->
        val expected = "abcd12319823y5"
        factory.create(key = Key.IN_MEMORY).use { inMemoryDriver ->
            inMemoryDriver.upsert("key", expected)

            assertNull(driver.get("key"))
            assertTrue(inMemoryDriver.isInMemory)
        }
    }

    @Test
    fun givenDriver_whenEmptyPassword_thenDoesNotEncrypt() = runDriverTest(key = Key.EMPTY) { factory, driver ->
        val expected = "asdljkgfnadsg"
        driver.upsert("key", expected)
        driver.close()

        val factory2 = SQLiteMCDriver.Factory(factory.config.dbName, TestDatabase.Schema) {
            filesystem(driver.config.filesystemConfig!!) {
                // remove encryptionConfig from inherited filesystem
                encryption {  }
            }
        }

        assertNotNull(factory2.config.filesystemConfig)
        assertNull(factory2.config.filesystemConfig!!.encryptionConfig)

        // Using rawKey (instead of Key.EMPTY) to verify that it will not be used
        // b/c there is no encryptionConfig
        factory2.create(keyRaw).use { driver2 ->
            assertEquals(expected, driver2.get("key"))
        }

        // Can open it with initial factory + empty key
        factory.create(Key.EMPTY).use { driver3 ->
            assertEquals(expected, driver3.get("key"))
        }

        // Trying non-empty password fails (b/c it's incorrect)
        assertFailsWith<IllegalStateException> {
            factory.create(keyRaw)
        }
    }

    @Test
    fun givenDriver_whenClose_thenCredentialsCleared() = runDriverTest { _, driver ->
        val expected = "198noinqgao"
        driver.upsert("key", expected)
        assertEquals(expected, driver.get("key"))
        driver.close()

        // The JdbcSqliteDriver doesn't actually close when close() is called
        // This is because of the ThreadedConnectionManager, close() is a no-op.
        // This should fail though because properties should have been cleared
        // of all credentials such that any further connection attempts should fail.
        assertFails { driver.get("key") }
    }

    @Test
    open fun givenDriver_whenRawKeys_thenAllVersionSucceed() {
        var i = 0

        listOf<Triple<Key, Key, FilesystemConfig.Builder.() -> Unit>>(
            Triple(keyRaw, keyRawWithSalt) { encryption { sqlCipher { default() } } },
            Triple(keyRaw, keyRawWithSalt) { encryption { sqlCipher { v1() } } },
            Triple(keyRaw, keyRawWithSalt) { encryption { sqlCipher { v2() } } },
            Triple(keyRaw, keyRawWithSalt) { encryption { sqlCipher { v3() } } },
            Triple(keyRaw, keyRawWithSalt) { encryption { sqlCipher { v4() } } },
            Triple(keyRaw, keyRawWithSalt) { encryption { chaCha20 { default() } } },
            Triple(keyRaw, keyRawWithSalt) { encryption { chaCha20 { sqleet() } } },

            Triple(keyRawWithSalt, keyRaw) { encryption { sqlCipher { default() } } },
            Triple(keyRawWithSalt, keyRaw) { encryption { sqlCipher { v1() } } },
            Triple(keyRawWithSalt, keyRaw) { encryption { sqlCipher { v2() } } },
            Triple(keyRawWithSalt, keyRaw) { encryption { sqlCipher { v3() } } },
            Triple(keyRawWithSalt, keyRaw) { encryption { sqlCipher { v4() } } },
            Triple(keyRawWithSalt, keyRaw) { encryption { chaCha20 { default() } } },
            Triple(keyRawWithSalt, keyRaw) { encryption { chaCha20 { sqleet() } } },
        ).forEach { (key1, key2, filesystem) ->
            logger("RUN - ${i++}")

            // db files automatically delete once runMCDriverTest completes.
            runDriverTest(key1, filesystem) { factory, driver ->
                val expected = "abcd12345"
                driver.upsert("key", expected)
                driver.close()

                factory.create(key1).use { driver2 ->
                    assertEquals(expected, driver2.get("key"))
                }

                assertFailsWith<IllegalStateException> {
                    factory.create(key2)
                }
                assertFailsWith<IllegalStateException> {
                    factory.create(keyPassphrase)
                }
            }
        }
    }

}
