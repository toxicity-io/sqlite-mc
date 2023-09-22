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
package io.toxicity.sqlite.mc.driver.config

import co.touchlab.sqliter.DatabaseFileContext
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver

/**
 * The directory in which databases reside.
 *
 * Passing a null or blank [path] will result in usage of the
 * system default location.
 *
 * In the event of an inability to establish the system default
 * location (very rare), [path] will be null and delegate
 * the exception handling to [SQLiteMCDriver.Factory.create] as
 * to not throw exception when [DatabasesDir] is instantiated.
 *
 * Default Locations:
 * - Native - Apple: `~/Documents/databases/`
 * - Native - Linux: `~/`
 * - Native - Mingw: `{drive}:\Users\{username}\`
 * */
public actual class DatabasesDir public actual constructor(path: String?) {

    public actual constructor(): this(null)

    public actual val path: String? by lazy {
        try {
            // Try to resolve the directory path
            DatabaseFileContext
                .databasePath("d", path?.ifBlank { null })
                .dropLast(2)
        } catch (t: Throwable) {
            t.printStackTrace()

            // Any failures are delegated to instantiation
            // of SQLiteMCDriver as to not crash the app
            // when DatabasesDir is created.
            null
        }
    }

    public actual override fun equals(other: Any?): Boolean = other is DatabasesDir && other.path == path
    public actual override fun hashCode(): Int = 17 * 31 + path.hashCode()
    public actual override fun toString(): String = path ?: ""
}
