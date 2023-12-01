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

import android.content.Context
import androidx.startup.AppInitializer
import androidx.startup.Initializer
import java.io.File

internal class SqliteMCInitializer: Initializer<SqliteMCInitializer.Impl> {

    internal class Impl private constructor() {

        internal companion object {

            @JvmField
            internal val INSTANCE: Impl = Impl()

            @JvmStatic
            internal fun Context.databasesDirFile(): File {
                val path = applicationContext.getDatabasePath("d")
                return path.parentFile ?: File(path.path.dropLast(1))
            }
        }

        @get:JvmName("databasesDir")
        internal var databasesDir: File? = null
            private set

        @get:JvmName("isInitialized")
        internal val isInitialized: Boolean get() = databasesDir != null

        @JvmSynthetic
        internal fun init(context: Context) {
            databasesDir = context.databasesDirFile()
        }
    }

    override fun create(context: Context): Impl {
        val appInitializer = AppInitializer.getInstance(context)
        check(appInitializer.isEagerlyInitialized(javaClass)) {
            """
                SqliteMCInitializer cannot be initialized lazily.
                Please ensure that you have:
                <meta-data
                    android:name='io.toxicity.sqlite.mc.driver.internal.SqliteMCInitializer'
                    android:value='androidx.startup' />
                under InitializationProvider in your AndroidManifest.xml
            """.trimIndent()
        }
        Impl.INSTANCE.init(context)
        return Impl.INSTANCE
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
