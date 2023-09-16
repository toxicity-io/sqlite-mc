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

import io.toxicity.sqlite.mc.driver.config.DatabasesDir
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * See [SQLiteMCDriverTest]
 * */
class SQLiteMCDriverJvmTest: SQLiteMCDriverTest() {

    override val databasesDir: DatabasesDir = File(
        System.getProperty("java.io.tmpdir", "/tmp"),
        "mc_driver_test"
    ).let { DatabasesDir(it) }
    override val logger: (log: String) -> Unit = { println(it)  }

    override fun deleteDbFile(directory: String, dbName: String) { File(directory, dbName).delete() }

    @Test
    @Ignore("Unused")
    fun stub() {}

}
