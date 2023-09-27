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

import app.cash.sqldelight.db.use
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.FilesystemConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import io.toxicity.sqlite.mc.driver.test.TestDatabase
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import kotlin.random.Random

internal expect fun filesystem(): FileSystem

abstract class TestHelperNonEphemeral: TestHelperBase() {

    protected fun runDriverTest(
        key: Key = this.keyPassphrase,
        // pass null to use in memory db
        filesystem: (FilesystemConfig.Builder.() -> Unit) = {},
        testLogger: ((String) -> Unit)? = this.testLogger,
        block: suspend TestScope.(factory: SQLiteMCDriver.Factory, driver: SQLiteMCDriver) -> Unit
    ): TestResult = runTest {
        val dbName = Random.Default.nextBytes(32).encodeToString(Base16) + ".db"

        deleteDatabaseFiles(dbName)

        val factory = SQLiteMCDriver.Factory(
            dbName = dbName,
            schema = TestDatabase.Schema,
            block = {
                logger = testLogger
                redactLogs = false

                filesystem(databasesDir, filesystem)
            }
        )

        var error: Throwable? = null

        try {
            factory.create(key).use { block(factory, it) }
        } catch (t: Throwable) {
            error = t
        } finally {
            deleteDatabaseFiles(dbName)
        }

        if (error != null) {
//            throw error
            error.printStackTrace()
        }
    }

    private fun deleteDatabaseFiles(dbName: String) {
        val db = databasesDir.path?.toPath()?.resolve(dbName) ?: return
        val journal = ("$db-journal").toPath()
        val shm = ("$db-shm").toPath()
        val wal = ("$db-wal").toPath()

        val fs = filesystem()
        listOf(db, journal, shm, wal).forEach { path ->
            try {
                fs.delete(path, mustExist = false)
            } catch (_: IOException) {}
        }
    }
}
