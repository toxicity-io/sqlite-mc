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
package io.toxicity.sqlite.mc.tests

import io.toxicity.sqlite.mc.withCommonConfiguration
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginTest {

    @Test
    fun `Creating Database automatically applies sqlite dialect dependency`() {
        val runner = GradleRunner.create()
            .withCommonConfiguration(File("src/test/kotlin-mpp"))

        val result = runner
            .withArguments("build", "--stacktrace")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val dependenciesResult = runner
            .withArguments("dependencies", "--stacktrace")
            .build()

        // TODO: Use injected dialect string
        assertTrue(dependenciesResult.output.contains("app.cash.sqldelight:sqlite-3-38-dialect:"))
    }

    @Test
    fun `Applying plugin adds dependencies when true`() {
        val runner = GradleRunner.create()
            .withCommonConfiguration(File("src/test/kotlin-mpp"))

        val result = runner
            .withArguments("build", "--stacktrace")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val dependenciesResult = runner
            .withArguments("dependencies", "--stacktrace")
            .build()

        assertTrue(dependenciesResult.output.contains("io.toxicity.sqlite-mc:driver:"))
    }

    @Test
    fun `Applying plugin does not add dependencies when false`() {
        val runner = GradleRunner.create()
            .withCommonConfiguration(File("src/test/kotlin-mpp-no-driver"))

        val result = runner
            .withArguments("build", "--stacktrace")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val dependenciesResult = runner
            .withArguments("dependencies", "--stacktrace")
            .build()

        assertFalse(dependenciesResult.output.contains("io.toxicity.sqlite-mc:driver:"))
    }
}
