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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.use
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.FilesystemConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import io.toxicity.sqlite.mc.driver.test.helper.TestHelperNonEphemeral
import kotlin.test.*

abstract class GeneralTest: TestHelperNonEphemeral() {

    @Test
    open fun givenDriver_whenOpened_thenIsSuccessful() = runDriverTest { _, driver ->
        val expected = "alskdjvn"
        driver.upsert("key", expected)
        assertEquals(expected, driver.get("key"))
    }

    @Test
    open fun givenDriver_whenReOpened_thenIsSuccessful() = runDriverTest { factory, driver ->
        val expected = "abcd123"
        driver.upsert("key", expected)

        driver.close()

        factory.create(keyPassphrase).use { driver2 ->
            assertEquals(expected, driver2.get("key"))
        }
    }

    @Test
    open fun givenDriver_whenIncorrectPassword_thenOpenFails() = runDriverTest { factory, driver ->
        driver.upsert("key", "asdfasdf")
        driver.close()

        assertFailsWith<IllegalStateException> {
            factory.create(Key.passphrase("incorrect"))
        }
    }

    @Test
    open fun givenDriver_whenEmptyPassword_thenDoesNotEncrypt() = runDriverTest(key = Key.Empty) { factory, driver ->
        val expected = "asdljkgfnadsg"
        driver.upsert("key", expected)
        driver.close()

        val factory2 = SQLiteMCDriver.Factory(factory.config.dbName, TestDatabase.Schema) {
            filesystem(driver.config.filesystemConfig) {
                // Opening with a different encryption
                // config should have no effect b/c
                // there should be no encryption set
                encryption { sqlCipher { v3() } }
            }
        }

        factory2.create(Key.Empty).use { driver2 ->
            assertEquals(expected, driver2.get("key"))
        }

        // Can open it with initial factory + empty key
        factory.create(Key.Empty).use { driver3 ->
            assertEquals(expected, driver3.get("key"))
        }

        // Trying non-empty password fails b/c it's incorrect (i.e. not Key.EMPTY)
        assertFailsWith<IllegalStateException> {
            factory.create(keyRaw)
        }
    }

    @Test
    open fun givenDriver_whenClose_thenCredentialsCleared() = runDriverTest { _, driver ->
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
            testLogger("RUN - ${i++}")

            // db files automatically delete once runMCDriverTest completes.
            runDriverTest(key1, filesystem = filesystem) { factory, driver ->
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

    @Test
    fun givenDriver_whenPragmas_thenSetsExpected() = runDriverTest(
        pragmas = {
            // SQLite3MultipleCiphers is compiled with 1 (true),
            // changing this to "fast" should result in it being set
            // to 2
            put("secure_delete", "fast")

            // These are set to false by default. This checks if
            // the parsing logic for true/false, 1/0, ON/OFF is
            // there for native, resulting in SQLite returning 1 (on)
            put("foreign_keys", "ON")
            put("recursive_triggers", "true")
        }
    ) { _, driver ->
        val mapper: (SqlCursor) -> QueryResult.Value<Long?> = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }

        val secureDelete = driver.executeQuery(null, "PRAGMA secure_delete", mapper, 0, null).value

        // 0 = false
        // 1 = true
        // 2 = fast
        assertEquals(2, secureDelete)

        val foreignKeys = driver.executeQuery(null, "PRAGMA foreign_keys", mapper, 0, null).value

        // 0 = false
        // 1 = true
        assertEquals(1, foreignKeys)

        val recursiveTriggers = driver.executeQuery(null, "PRAGMA recursive_triggers", mapper, 0, null).value

        // 0 = false
        // 1 = true
        assertEquals(1, recursiveTriggers)
    }

}
