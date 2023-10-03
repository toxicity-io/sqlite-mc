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
package io.toxicity.sqlite.mc.gradle

import app.cash.sqldelight.VERSION
import app.cash.sqldelight.gradle.SqlDelightDatabase
import app.cash.sqldelight.gradle.SqlDelightDsl
import app.cash.sqldelight.gradle.SqlDelightExtension
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Property

@SqlDelightDsl
abstract class SQLiteMCExtension internal constructor(
    private val delegate: SqlDelightExtension,
) {

    /**
     * Default: true
     *
     * Will:
     *  - Add the dependency for SQLiteMCDriver
     *  - Link to SQLite3MultipleCiphers (if native)
     *
     * There are some circumstances where setting to false may
     * be desired, such as in a multi-module project where
     * tests or other such runtime logic lives elsewhere.
     * */
    abstract val addDriverAndLink: Property<Boolean>

    val databases: NamedDomainObjectContainer<SqlDelightDatabase> get() {
        val container = delegate.databases

        @Suppress("RedundantSamConstructor")
        container.whenObjectAdded(Action {
            it.dialect("app.cash.sqldelight:sqlite-3-38-dialect:${VERSION}")
        })

        return container
    }
}
