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
import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared {

        androidLibrary(namespace = "io.toxicity.sqlite.mc.driver.test") {
            sourceSetTest {
                dependencies {
                    implementation(project(":library:android-unit-test"))
                }
            }
            sourceSetTestInstrumented {
                dependencies {
                    implementation(libs.androidx.test.core)
                    implementation(libs.androidx.test.runner)
                }
            }
        }

        common {
            pluginIds(libs.plugins.sql.delight.get().pluginId)

            sourceSetTest {
                dependencies {
                    implementation(project(":library:driver"))
                    implementation(libs.kotlinx.coroutines.test)
                    implementation(libs.encoding.base16)
                }
            }
        }

        kotlin {
            extensions.configure<SqlDelightExtension>("sqldelight") {
                databases {
                    linkSqlite.set(false)

                    create("TestDatabase") {
                        packageName.set("io.toxicity.sqlite.mc.driver.test")
                        srcDirs("src/sqldelight")
                    }
                }
            }
        }
    }
}
