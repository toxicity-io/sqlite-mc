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

import io.toxicity.sqlite.mc.driver.EphemeralOpt
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.test.helper.TestHelperBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

abstract class EphemeralTest: TestHelperBase() {

    private val factory = SQLiteMCDriver.Factory("test.db", TestDatabase.Schema) {
        logger = testLogger
        redactLogs = false

        pragmas {
            put("secure_delete", "false")
        }

        filesystem(databasesDir) {
            // defaults
        }
    }

    private val opts = listOf(
        EphemeralOpt.IN_MEMORY,
        EphemeralOpt.NAMED,
        EphemeralOpt.TEMPORARY,
    )

    @Test
    fun givenEphemeralDriver_whenMultiple_thenAreSeparate() = runTest {
        opts.forEach { opt ->
            if (opt == EphemeralOpt.NAMED) return@forEach

            val driver1 = factory.createBlocking(opt)
            val expected = "not-shared"
            driver1.upsert("key", expected)
            assertEquals(expected, driver1.get("key"))

            val driver2 = factory.createBlocking(opt)
            assertNull(driver2.get("key"))

            driver1.close()
            driver2.close()
        }
    }

    @Test
    fun givenEphemeralNamedDriver_whenMultiple_thenAreShared() {
        val driver1 = factory.createBlocking(EphemeralOpt.NAMED)
        val expected = "shared"
        driver1.upsert("key", expected)
        assertEquals(expected, driver1.get("key"))

        val driver2 = factory.createBlocking(EphemeralOpt.NAMED)
        assertEquals(expected, driver2.get("key"))
        driver1.close()
        assertEquals(expected, driver2.get("key"))
        driver2.close()

        val driver3 = factory.createBlocking(EphemeralOpt.NAMED)
        assertNull(driver3.get("key"))
        driver3.close()
    }

    @Test
    fun givenEphemeralDriver_whenClosed_thenQueryFails() {
        opts.forEach { opt ->
            val driver = factory.createBlocking(opt)
            driver.close()
            assertFails { driver.get("key") }
        }
    }

    @Test
    fun givenEphemeralDriver_whenMultipleCloses_thenDoesNotThrowException() {
        opts.forEach { opt ->
            val driver = factory.createBlocking(opt)
            driver.close()
            driver.close()
        }
    }

}
