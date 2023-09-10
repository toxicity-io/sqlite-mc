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
package io.toxicity.sqlite.mc.driver.config.encryption

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyUnitTest {

    private fun random(size: Int): ByteArray = Random.Default.nextBytes(size)

    @Test
    fun givenRawKey_whenFillKeyTrue_thenKeyBytesAreZeroed() {
        val keyBytes = random(32)
        val expected = keyBytes.copyOf()

        Key.raw(key = keyBytes, salt = null, fillKey = true)

        assertContentEquals(ByteArray(keyBytes.size), keyBytes)

        try {
            assertContentEquals(expected, keyBytes)
            throw IllegalStateException("keyBytes were not filled")
        } catch (_: AssertionError) {
            // pass
        }
    }

    @Test
    fun givenRawKey_whenFillKeyFalse_thenKeyBytesAreNotZeroed() {
        val keyBytes = random(32)
        val expected = keyBytes.copyOf()

        Key.raw(key = keyBytes, salt = null, fillKey = false)

        assertContentEquals(expected, keyBytes)
    }

    @Test
    fun givenRawKeyWithSalt_whenFillKeyTrue_thenSaltIsNotFilled() {
        val salt = random(16)
        val expected = salt.copyOf()

        Key.raw(key = random(32), salt = salt, fillKey = true)

        assertContentEquals(expected, salt)
    }

    @Test
    fun givenRawKey_whenEncoded_thenIsSingleLine() {
        val key = Key.raw(key = random(32), salt = null)
        val formatted = key.retrieveFormatted(cipher = Cipher.SQLCIPHER)

        assertEquals(1, formatted.lines().size)
    }

    @Test
    fun givenRawKeyWithSalt_whenEncoded_thenIsSingleLine() {
        val key = Key.raw(key = random(32), salt = random(16))
        val formatted = key.retrieveFormatted(cipher = Cipher.SQLCIPHER)

        assertEquals(1, formatted.lines().size)
    }

    @Test
    fun givenRawKey_whenKeyNot32Bytes_thenThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            Key.raw(key = random(33), salt = null)
        }
    }

    @Test
    fun givenRawKeyWithSalt_whenSaltNot16Bytes_thenThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            Key.raw(key = random(32), salt = random(17))
        }
    }
}
