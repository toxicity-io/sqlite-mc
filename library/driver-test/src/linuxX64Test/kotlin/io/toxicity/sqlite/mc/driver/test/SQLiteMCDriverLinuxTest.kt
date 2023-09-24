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
package io.toxicity.sqlite.mc.driver.test

import kotlinx.coroutines.test.TestResult
import kotlin.test.Test

/**
 * See [SQLiteMCDriverTest]
 * */
class SQLiteMCDriverLinuxTest: SQLiteMCDriverTest() {

    @Test
    fun stub() {}

    @Test
    override fun givenDriver_whenOpened_thenIsSuccessful(): TestResult {
        // TODO: https://github.com/toxicity-io/sqlite-mc-driver/pull/24#issuecomment-1729836134
    }

    @Test
    override fun givenDriver_whenReOpened_thenIsSuccessful(): TestResult {
        // TODO: https://github.com/toxicity-io/sqlite-mc-driver/pull/24#issuecomment-1729836134
    }

    @Test
    override fun givenDriver_whenIncorrectPassword_thenOpenFails(): TestResult {
        // TODO: https://github.com/toxicity-io/sqlite-mc-driver/pull/24#issuecomment-1729836134
    }

    @Test
    override fun givenDriver_whenEmptyPassword_thenDoesNotEncrypt(): TestResult {
        // TODO: https://github.com/toxicity-io/sqlite-mc-driver/pull/24#issuecomment-1729836134
    }

    @Test
    override fun givenDriver_whenClose_thenCredentialsCleared(): TestResult {
        // TODO: https://github.com/toxicity-io/sqlite-mc-driver/pull/24#issuecomment-1729836134
    }

    @Test
    override fun givenDriver_whenRawKeys_thenAllVersionSucceed() {
        // TODO: Fix raw keys for native driver
    }

}
