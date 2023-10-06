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

import io.toxicity.sqlite.mc.driver.config.MCPragma
import io.toxicity.sqlite.mc.driver.config.MutableMCPragmas
import io.toxicity.sqlite.mc.driver.config.mutableMCPragmas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MCCipherConfigUnitTest {

    @Test
    fun givenConfig_whenDefaultNoChanges_thenContainsCipherAndLegacy() {
        val list = mutableListOf<MutableMCPragmas>()

        list.addPragmas { chaCha20 { default() } }
        list.addPragmas { rc4 { default() } }
        list.addPragmas { sqlCipher { default() } }
        list.addPragmas { wxAES128 { default() } }
        list.addPragmas { wxAES256 { default() } }

        list.forEach { pragmas ->
            assertEquals(2, pragmas.size)
            pragmas.assertAtIndex<MCPragma.CIPHER>(index = 0)
            pragmas.assertAtIndex<MCPragma.LEGACY>(index = 1)
        }
    }

    @Test
    fun givenConfig_whenLegacyNoChanges_thenContainsLegacy() {
        val list = mutableListOf<MutableMCPragmas>()

        list.addPragmas { chaCha20 { sqleet() } }

        // rc4 is always in legacy mode
        list.addPragmas { rc4 { default() } }

        list.addPragmas { sqlCipher { v1() } }
        list.addPragmas { sqlCipher { v2() } }
        list.addPragmas { sqlCipher { v3() } }
        list.addPragmas { sqlCipher { v4() } }
        list.addPragmas { wxAES128 { default { legacy(1) } } }
        list.addPragmas { wxAES256 { default { legacy(1) } } }

        list.forEach { pragmas ->
            assertEquals(2, pragmas.size)
            pragmas.assertAtIndex<MCPragma.CIPHER>(index = 0)
            pragmas.assertAtIndex<MCPragma.LEGACY>(index = 1)
        }
    }

    @Test
    fun givenConfig_whenNonDefaultLegacyPageSize_thenContainsLegacyPageSize() {
        val list = mutableListOf<MutableMCPragmas>()

        list.addPragmas { chaCha20 { default { legacyPageSize(65536) } } }
        list.addPragmas { rc4 { default { legacyPageSize(65536) } } }
        list.addPragmas { sqlCipher { default { legacyPageSize(65536) } } }
        list.addPragmas { wxAES128 { default { legacyPageSize(65536) } } }
        list.addPragmas { wxAES256 { default { legacyPageSize(65536) } } }

        list.forEach { pragmas ->
            assertEquals(3, pragmas.size)
            // CIPHER
            // LEGACY
            pragmas.assertAtIndex<MCPragma.LEGACY_PAGE_SIZE>(index = 2)
        }
    }

    @Test
    fun givenConfig_whenSQLCipherNullableFields_thenFallsBackToDefault() {
        val list = mutableListOf<MutableMCPragmas>()

        // If null, building pragmas should fall back to the default value
        // i.e. the PRAGMA hmac_pngo should not be set
        list.addPragmas { sqlCipher { default {
            hmacPngo(null)
            hmacSaltMask(null)
            plaintextHeaderSize(null)
        } } }
        list.addPragmas { sqlCipher { v4 {
            // Should all revert back to default values and then
            // not be applied (no diff with v4)
            hmacPngo(null)
            hmacSaltMask(null)
            plaintextHeaderSize(null)
        } } }
        list.addPragmas { sqlCipher { v3 {
            hmacPngo(null)
            hmacSaltMask(null)
            plaintextHeaderSize(32) // should build with null
        } } }
        list.addPragmas { sqlCipher { v2 {
            hmacPngo(null)
            hmacSaltMask(null)
            plaintextHeaderSize(32) // should build with null
        } } }

        list.addPragmas { sqlCipher { v1 {
            // should build with all null values
            hmacPngo(HmacPngo.BIG_ENDIAN)
            hmacSaltMask(Byte.MAX_VALUE)
            plaintextHeaderSize(32)
        } } }

        list.forEach { pragmas ->
            assertFalse(pragmas.containsKey(MCPragma.HMAC_PNGO))
            assertFalse(pragmas.containsKey(MCPragma.HMAC_SALT_MASK))
            assertFalse(pragmas.containsKey(MCPragma.PLAIN_TEXT_HEADER_SIZE))
        }
    }

    // Ordering of entries is important. For example, PRAGMA cipher **must** come first,
    // followed by PRAGMA legacy (if non-default for given cipher). This ensures that default
    // settings are set before any other PRAGMA statements for non-default values are executed.
    // If PRAGMA legacy were to come after configuring some other setting, that other setting
    // would be re-set to whatever the default for that PRAGMA legacy value was.
    //
    // Another one is that PRAGMA legacy_page_size (if non-default for given cipher) **must**
    // come before PRAGMA key.
    private inline fun <reified T: MCPragma<*>> MutableMCPragmas.assertAtIndex(index: Int) {
        var i = 0
        forEach { entry ->
            if (i++ == index) {
                assertTrue(entry.key is T)
                return
            }
        }

        throw AssertionError("${T::class} was not found")
    }

    @Throws(IllegalStateException::class)
    private fun MutableList<MutableMCPragmas>.addPragmas(
        block: EncryptionConfig.Builder.() -> Unit
    ) {
        val config = EncryptionConfig.new(other = null) { block() }

        val pragmas = mutableMCPragmas()
        config.cipherConfig.applyPragmas(pragmas)
        config.applyPragmas(pragmas)
        add(pragmas)
    }

}
