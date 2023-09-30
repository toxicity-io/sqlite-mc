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
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.android.library) apply(false)
    alias(libs.plugins.binary.compat)
    alias(libs.plugins.cklib) apply(false)
    alias(libs.plugins.kotlin.multiplatform) apply(false)
    alias(libs.plugins.sql.delight) apply(false)
}

allprojects {

    findProperty("GROUP")?.let { group = it }
    findProperty("VERSION_NAME")?.let { version = it }
    findProperty("POM_DESCRIPTION")?.let { description = it.toString() }

    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()

        if (version.toString().endsWith("-SNAPSHOT")) {
            // Only allow snapshot dependencies for non-release versions.
            // This would cause a build failure if attempting to make a release
            // while depending on a -SNAPSHOT version (such as core or hash).
            maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            maven("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}

@Suppress("LocalVariableName")
apiValidation {
    val CHECK_PUBLICATION = findProperty("CHECK_PUBLICATION") as? String

    if (CHECK_PUBLICATION != null) {
        ignoredProjects.add("check-publication")
    } else {
        val KMP_TARGETS_ALL = System.getProperty("KMP_TARGETS_ALL") != null
        val TARGETS = (findProperty("KMP_TARGETS") as? String)?.split(',')
        val JVM = TARGETS?.contains("JVM") != false
        val ANDROID = TARGETS?.contains("ANDROID") != false

        // Don't check these projects when building JVM only or ANDROID only
        val IGNORE_JVM_ANDROID = !KMP_TARGETS_ALL && ((!ANDROID && JVM) || (ANDROID && !JVM))

        if (IGNORE_JVM_ANDROID) {
            ignoredProjects.add("driver")
        }
        ignoredProjects.add("driver-test")
    }
}
