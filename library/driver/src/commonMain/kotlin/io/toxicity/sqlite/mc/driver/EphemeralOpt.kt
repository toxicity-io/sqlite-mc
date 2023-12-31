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

package io.toxicity.sqlite.mc.driver

public enum class EphemeralOpt {

    /**
     * Creates a purely in-memory database using `":memory:"`
     * as the SQLite path.
     * */
    IN_MEMORY,

    /**
     * Creates a named in-memory database using
     * `"file:{dbName}?mode=memory&cache=shared"` as the
     * SQLite path.
     *
     * NOTE: A named in-memory database will stay open
     * until **all** connections with it are closed. It
     * can be shared between multiple connections.
     * */
    NAMED,

    /**
     * Creates a temporary database using `""` (an empty value)
     * as the SQLite path.
     *
     * NOTE: A Temporary database differs from [IN_MEMORY] in that
     * it remains mostly in-memory, but SQLite will dump data to
     * a temp file when needed.
     *
     * NOTE: SQLite3MultipleCiphers is compiled with flag
     * `SQLITE_TEMP_STORE=2` which treats this exactly like
     * [IN_MEMORY]. If you wish to use the disk caching
     * behavior, you **must** execute `PRAGMA temp_store = 1;`
     * when the connection is initially created.
     * */
    TEMPORARY,
}
