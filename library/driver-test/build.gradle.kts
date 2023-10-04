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
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared {

        androidLibrary(namespace = "io.toxicity.sqlite.mc.driver.test") {
            sourceSetTest {
                findProject(":library:android-unit-test")?.let { androidUnitTest ->
                    dependencies {
                        implementation(androidUnitTest)
                    }
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

            sourceSetMain {
                dependencies {
                    implementation(project(":library:driver"))
                }
            }

            sourceSetTest {
                dependencies {
                    implementation(libs.encoding.base16)
                    implementation(libs.kotlinx.coroutines.test)
                    implementation(libs.okio)
                }
            }
        }

        kotlin {
            extensions.configure<SqlDelightExtension>("sqldelight") {
                linkSqlite.set(false)

                targets.filterIsInstance<KotlinNativeTarget>()
                    .filter { it.konanTarget.family.isAppleFamily }
                    .flatMap { it.binaries }
                    .forEach { compilationUnit ->
                        compilationUnit.linkerOpts("-framework", "Security")
                    }

                databases {
                    create("TestDatabase") {
                        packageName.set("io.toxicity.sqlite.mc.driver.test")
                        srcDirs("src/sqldelight")
                    }
                }
            }
        }
    }
}
