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
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply(false)
    alias(libs.plugins.sqlitemc)
    alias(libs.plugins.kmp.configuration)
}

kmpConfiguration {
    configure {
        jvm()

        linuxAll()
        mingwAll()

        // Local publications will not be present unless the host is macOS
        if (HostManager.hostIsMac) {
            iosAll()
            macosAll()
            tvosAll()
            watchosAll()
        }

        kotlin {
            sqliteMC {
                databases {
                    create("Database") {
                        packageName.set("com.sample")
                    }
                }
            }
        }
    }
}
