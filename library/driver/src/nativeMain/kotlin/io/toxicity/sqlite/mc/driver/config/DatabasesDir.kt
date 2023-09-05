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

/**
 * The directory in which databases reside.
 *
 * Passing a blank [path] will result in using
 * the system default location for native clients.
 * */
public actual class DatabasesDir public constructor(path: String?) {

    /**
     * Use the system default location.
     * */
    public constructor(): this(null)

    private var isInitialized: Boolean = false

    internal actual val path: String by lazy {
        isInitialized = true

        // This call will not only resolve the path, but
        // create the directory if datapathPath is null.
        //
        // We do not want to create the directory upon
        // initialization because it may result in an
        // exception.
        //
        // `path` will only be called when creating
        // a new driver, which will be handled there
        DatabaseFileContext.databasePath(
            databaseName = "",
            datapathPath = path?.ifBlank { null },
        )
    }

    public actual fun pathOrNull(): String? = if (isInitialized) path else null

    public actual override fun equals(other: Any?): Boolean = commonEquals(other)
    public actual override fun hashCode(): Int = commonHashCode()
    public actual override fun toString(): String = commonToString()
}
