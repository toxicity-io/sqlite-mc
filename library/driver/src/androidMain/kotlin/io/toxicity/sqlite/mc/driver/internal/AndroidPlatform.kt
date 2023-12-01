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
package io.toxicity.sqlite.mc.driver.internal

import java.io.File

internal actual val DEFAULT_DATABASES_DIR: File? by lazy {
    SqliteMCInitializer.Impl.INSTANCE.databasesDir ?: run {

        if (System.getProperty("java.runtime.name")?.contains("android", ignoreCase = true) == true) {
            // It's android runtime and androidx.startup was not initialized... something
            // is very wrong. Set to null so exception is lazily thrown.
            null
        } else {
            // Android unit tests. Default to temporary directory for the host machine
            System.getProperty("java.io.tmpdir")
                ?.ifBlank { null }
                ?.let { tmp -> File(tmp).resolve("sqlite_mc").resolve(".databases") }
        }
    }
}
