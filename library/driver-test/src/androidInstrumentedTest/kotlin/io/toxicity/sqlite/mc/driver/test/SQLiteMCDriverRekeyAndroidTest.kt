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

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import io.toxicity.sqlite.mc.driver.config.DatabasesDir
import io.toxicity.sqlite.mc.driver.config.databasesDir
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * See [SQLiteMCDriverRekeyTest]
 * */
class SQLiteMCDriverRekeyAndroidTest: SQLiteMCDriverRekeyTest() {

    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    override val databasesDir: DatabasesDir = ctx.databasesDir()
    override val logger: (log: String) -> Unit = { Log.d("TEST", it)  }

    override fun deleteDbFile(directory: String, dbName: String) { File(directory, dbName).delete() }

    @Test
    @Ignore("Unused")
    fun stub() {}

}
