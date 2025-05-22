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

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.toxicity.sqlite.mc.driver.config

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
 * - Android Runtime: `/data/user/{userid}/{your.application.id}/databases/`
 * - Android Unit Tests: `{tmp dir}/sqlite_mc/.databases`
 * - Jvm - Unix: `~/.databases/`
 * - Jvm - Mingw: `{drive}:\Users\{username}\.databases\`
 * - Native - Apple: `~/Documents/databases/`
 * - Native - Linux: `~/`
 * - Native - Mingw: `{drive}:\Users\{username}\`
 *
 * For Android, also see [io.toxicity.sqlite.mc.driver.config.databasesDir]
 * */
public expect class DatabasesDir(path: String?) {

    public val path: String?

    public constructor()

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
