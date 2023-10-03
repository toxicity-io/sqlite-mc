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

import app.cash.sqldelight.gradle.SqlDelightExtension
import org.gradle.api.Plugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer

abstract class SQLiteMCPlugin internal constructor(): Plugin<Project> {

    final override fun apply(project: Project) {
        project.plugins.apply("app.cash.sqldelight")

        val delegate = project.extensions.getByType(SqlDelightExtension::class.java)
        delegate.linkSqlite.set(false)

        val extension = project.extensions.create("sqliteMC", SQLiteMCExtension::class.java, delegate)
        extension.addDriverAndLink.convention(true)

        project.afterEvaluate {
            project.setupSQLiteMC(extension)
        }
    }

    private fun Project.setupSQLiteMC(extension: SQLiteMCExtension) {
        if (!extension.addDriverAndLink.get()) return

        val isMultiplatform = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val sourceSetMainName = if (isMultiplatform) "commonMain" else "main"
        val sourceSets = extensions
            .getByType(KotlinSourceSetContainer::class.java)
            .sourceSets

        // Add driver dependency
        configurations
            .getByName(
                sourceSets
                    .getByName(sourceSetMainName)
                    .apiConfigurationName
            )
            .dependencies
            // TODO: Inject Version
            .add(dependencies.create("io.toxicity.sqlite-mc:driver:2.0.0-1.6.4-0-SNAPSHOT"))

        // TODO: Add android-unit-test dependency to android source sets (if available)

        extensions
            .findByType(KotlinMultiplatformExtension::class.java)
            ?.linkSQLite3MultipleCiphers()
    }

    private fun KotlinMultiplatformExtension.linkSQLite3MultipleCiphers() {
        targets
            .filterIsInstance<KotlinNativeTarget>()
            .filter { it.konanTarget.family.isAppleFamily }
            .flatMap { it.binaries }
            .forEach { it.linkerOpts("-framework", "Security") }
    }
}
