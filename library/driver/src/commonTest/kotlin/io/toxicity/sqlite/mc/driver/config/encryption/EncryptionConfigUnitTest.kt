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

import io.toxicity.sqlite.mc.driver.config.pragma.Pragma
import io.toxicity.sqlite.mc.driver.config.pragma.mutablePragmas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncryptionConfigUnitTest {

    @Test
    fun givenConfig_whenNoCipher_thenThrowsException() {
        assertFailsWith<IllegalStateException> {
            EncryptionConfig.new(other = null) {}
        }
        assertFailsWith<IllegalStateException> {
            EncryptionConfig.new(other = null) { chaCha20 {
                // options not chosen
            } }
        }
    }

    @Test
    fun givenConfig_whenChaCha20Default_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            chaCha20 { default() }
        }

        assertEquals(MCChaCha20Config.Default, config.cipherConfig)

        val config2 = config.new {
            chaCha20 { default { legacy(MCChaCha20Config.SQLeet.legacy) } }
        }

        assertEquals(MCChaCha20Config.SQLeet.legacy, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenChaCha20SQLeet_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            chaCha20 { sqleet() }
        }

        assertEquals(MCChaCha20Config.SQLeet, config.cipherConfig)

        val config2 = config.new {
            chaCha20 { sqleet { legacy(MCChaCha20Config.Default.legacy) } }
        }

        assertEquals(MCChaCha20Config.Default.legacy, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenRC4Default_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            rc4 { default() }
        }

        assertEquals(MCRC4Config.Default, config.cipherConfig)

        val config2 = config.new {
            rc4 { default { legacyPageSize(1024) } }
        }

        assertEquals(1024, config2.cipherConfig.legacyPageSize)
    }

    @Test
    fun givenConfig_whenSQLCipherV1_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            sqlCipher { v1() }
        }

        assertEquals(MCSqlCipherConfig.v1, config.cipherConfig)

        val config2 = config.new {
            sqlCipher { v1 { legacy(MCSqlCipherConfig.v2.legacy) } }
        }

        assertEquals(MCSqlCipherConfig.v2.legacy, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenSQLCipherV2_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            sqlCipher { v2() }
        }

        assertEquals(MCSqlCipherConfig.v2, config.cipherConfig)

        val config2 = config.new {
            sqlCipher { v2 { legacy(MCSqlCipherConfig.v3.legacy) } }
        }

        assertEquals(MCSqlCipherConfig.v3.legacy, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenSQLCipherV3_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            sqlCipher { v3() }
        }

        assertEquals(MCSqlCipherConfig.v3, config.cipherConfig)

        val config2 = config.new {
            sqlCipher { v3 { legacy(MCSqlCipherConfig.v4.legacy) } }
        }

        assertEquals(MCSqlCipherConfig.v4.legacy, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenSQLCipherV4_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            sqlCipher { v4() }
        }

        assertEquals(MCSqlCipherConfig.v4, config.cipherConfig)

        val config2 = config.new {
            sqlCipher { v4 { legacy(MCSqlCipherConfig.v1.legacy) } }
        }

        assertEquals(MCSqlCipherConfig.v1.legacy, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenSQLCipherDefault_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            sqlCipher { default() }
        }

        assertEquals(MCSqlCipherConfig.Default, config.cipherConfig)

        val config2 = config.new {
            sqlCipher { default { legacy(MCSqlCipherConfig.v1.legacy) } }
        }

        assertEquals(MCSqlCipherConfig.v1.legacy, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenWxAES128_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            wxAES128 { default() }
        }

        assertEquals(MCWxAES128Config.Default, config.cipherConfig)

        val config2 = config.new {
            wxAES128 { default { legacy(1) } }
        }

        assertEquals(0, config.cipherConfig.legacy)
        assertEquals(1, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenWxAES256_thenCipherIsSet() {
        val config = EncryptionConfig.new(other = null) {
            wxAES256 { default() }
        }

        assertEquals(MCWxAES256Config.Default, config.cipherConfig)

        val config2 = config.new {
            wxAES256 { default { legacy(1) } }
        }

        assertEquals(0, config.cipherConfig.legacy)
        assertEquals(1, config2.cipherConfig.legacy)
    }

    @Test
    fun givenConfig_whenApplyPragmas_cipherConfigPragmasAreApplied() {
        val config = EncryptionConfig.new(other = null) {
            mcLegacyWAL(true)
            sqlCipher {
                v3 {
                    kdfIter(250_000)
                }
            }
        }

        val pragmas = mutablePragmas()
        config.applyPragmas(pragmas)
        assertEquals(Pragma.MC.CIPHER, pragmas.toList().first().first)
    }

}
