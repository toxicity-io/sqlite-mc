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
@file:Suppress("UnstableApiUsage")

import io.matthewnelson.kmp.configuration.extension.KmpConfigurationExtension
import io.matthewnelson.kmp.configuration.extension.container.target.KmpConfigurationContainerDsl
import io.matthewnelson.kmp.configuration.extension.container.target.TargetAndroidContainer
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.AbstractCompile

fun KmpConfigurationExtension.configureShared(
    action: (Action<KmpConfigurationContainerDsl>)? = null,
) {
    configure {
        options {
            useUniqueModuleNames = true
        }

        jvm {
            // TODO: Remove once gradle-kmp-configuration-plugin is updated to 0.4.1+
            target {
                val targetName = name
                project.tasks.withType(AbstractCompile::class.java) {
                    val task = this
                    if (!task.name.startsWith("compile$targetName", ignoreCase = true)) return@withType
                    when {
                        task.name.endsWith("MainJava") -> {}
                        task.name.endsWith("TestJava") -> {}
                        else -> return@withType
                    }
                    task.sourceCompatibility = JavaVersion.VERSION_1_8.toString()
                    task.targetCompatibility = JavaVersion.VERSION_1_8.toString()
                }
            }

            kotlinJvmTarget = JavaVersion.VERSION_1_8
        }

        // https://github.com/touchlab/SQLiter/issues/117
//        androidNativeAll()
        iosAll()
        linuxAll()
        macosAll()
        mingwAll()
        tvosAll()
        watchosAll()

        common {
            sourceSetTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }

        kotlin { explicitApi() }

        action?.execute(this)
    }
}

fun KmpConfigurationContainerDsl.androidLibrary(
    namespace: String,
    buildTools: String? = "34.0.0",
    compileSdk: Int = 34,
    minSdk: Int = 21,
    javaVersion: JavaVersion = JavaVersion.VERSION_1_8,
    action: (Action<TargetAndroidContainer.Library>)? = null,
) {
    androidLibrary {
        android {
            buildTools?.let { buildToolsVersion = it }
            this.compileSdk = compileSdk
            this.namespace = namespace

            defaultConfig {
                this.minSdk = minSdk

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testInstrumentationRunnerArguments["disableAnalytics"] = "true"
            }

            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
        }

        kotlinJvmTarget = javaVersion
        compileSourceCompatibility = javaVersion
        compileTargetCompatibility = javaVersion

        action?.execute(this)
    }
}
