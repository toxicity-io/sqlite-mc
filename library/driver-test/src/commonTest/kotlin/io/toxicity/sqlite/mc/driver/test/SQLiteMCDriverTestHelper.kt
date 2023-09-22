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
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.DatabasesDir
import io.toxicity.sqlite.mc.driver.config.FilesystemConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

internal expect fun Path.deleteRecursively()

abstract class SQLiteMCDriverTestHelper {

    protected val databasesDir: DatabasesDir = DatabasesDir(
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("mc_driver_test").toString()
    )
    protected open val logger: (log: String) -> Unit = { log -> println(log) }

    protected val keyPassphrase = Key.passphrase(value = "password")
    protected val keyRaw = Key.raw(
        key = Random.Default.nextBytes(32),
        salt = null,
        fillKey = false,
    )
    protected val keyRawWithSalt = Key.raw(
        key = Random.Default.nextBytes(32),
        salt = Random.Default.nextBytes(16),
        fillKey = false,
    )

    protected fun SQLiteMCDriver.toTestDatabase(): TestDatabase = TestDatabase(this)
    protected fun SQLiteMCDriver.upsert(key: String, value: String) {
        toTestDatabase().testQueries.upsert(value = value, key = key)
    }
    protected fun SQLiteMCDriver.get(key: String): String? {
        return toTestDatabase().testQueries.get(key).executeAsOneOrNull()
    }

    protected fun runDriverTest(
        key: Key? = this.keyPassphrase,
        // pass null to use in memory db
        filesystem: (FilesystemConfig.Builder.() -> Unit)? = { encryption { chaCha20 { default() } } },
        testLogger: ((String) -> Unit)? = this.logger,
        block: suspend TestScope.(factory: SQLiteMCDriver.Factory, driver: SQLiteMCDriver) -> Unit
    ): TestResult = runTest {
        val dbName = Random.Default.nextBytes(32).encodeToString(Base16) + ".db"

        databasesDir.path?.toPath()?.resolve(dbName)?.deleteRecursively()

        val factory = SQLiteMCDriver.Factory(
            dbName = dbName,
            schema = TestDatabase.Schema,
            block = {
                logger = testLogger
                redactLogs = false

                if (filesystem == null) return@Factory
                filesystem(databasesDir, filesystem)
            }
        )

        var error: Throwable? = null

        try {
            factory.create(key).use { block(factory, it) }
        } catch (t: Throwable) {
            error = t
        } finally {
            databasesDir.path?.toPath()?.resolve(dbName)?.deleteRecursively()

            error?.let { ex ->
                val msg = factory
                    .config
                    .filesystemConfig
                    ?.toString()

                throw IllegalStateException(msg, ex)
            }
        }
    }
}
