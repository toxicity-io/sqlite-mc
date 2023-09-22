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

import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

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
 * - Android: `/data/user/{userid}/{your.application.id}/databases/`
 * - Jvm - Unix: `~/.databases/`
 * - Jvm - Mingw: `{drive}:\Users\{username}\.databases\`
 *
 * For Android, also see [io.toxicity.sqlite.mc.driver.config.databasesDir]
 * */
public actual class DatabasesDir public actual constructor(path: String?) {

    public actual constructor(): this(null)
    public constructor(directory: File): this(directory.path)
    public constructor(directory: Path): this(directory.pathString)

    @JvmField
    public actual val path: String? = if (!path.isNullOrBlank()) {
        File(path).absolutePath
    } else {
        DEFAULT_DIR
    }

    public actual override fun equals(other: Any?): Boolean = other is DatabasesDir && other.path == path
    public actual override fun hashCode(): Int = 17 * 31 + path.hashCode()
    public actual override fun toString(): String = path ?: ""

    private companion object {

        private val DEFAULT_DIR: String? by lazy {
            var defaultDir: File? = null

            if (
                System.getProperty("os.name")?.contains("Linux", ignoreCase = true) == true
                && System.getProperty("java.vendor")?.equals("The Android Project", ignoreCase = true) == true
            ) {
                val cache: String? = System.getProperty("java.io.tmpdir")

                defaultDir = if (
                    cache?.startsWith("/data/user/") == true
                    && cache.endsWith("/cache")
                ) {
                    File(cache)
                } else {
                    val dexcache: String? = System.getProperty("dexmaker.dexcache")

                    if (
                        dexcache?.startsWith("/data/user/") == true
                        && dexcache.endsWith("/app_dxmaker_cache")
                    ) {
                        File(dexcache)
                    } else {
                        null
                    }
                }?.resolveSibling("databases")
            }

            if (defaultDir == null) {
                defaultDir = System.getProperty("user.home")
                    ?.ifBlank { null }
                    ?.let { File(it).resolve(".databases") }
            }

            defaultDir?.absolutePath
        }
    }
}
