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
package io.toxicity.sqlite.mc.driver.test.helper

import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.DatabasesDir
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import io.toxicity.sqlite.mc.driver.test.TestDatabase
import okio.FileSystem
import kotlin.random.Random

abstract class TestHelperBase {

    protected val databasesDir: DatabasesDir = DatabasesDir(
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("mc_driver_test").toString()
    )
    protected open val testLogger: (log: String) -> Unit = { log -> println(log) }

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
    protected fun SQLiteMCDriver.upsert(
        key: String,
        value: String
    ) = toTestDatabase().testQueries.upsert(value = value, key = key)
    protected fun SQLiteMCDriver.get(
        key: String
    ): String? = toTestDatabase().testQueries.get(key).executeAsOneOrNull()
}
