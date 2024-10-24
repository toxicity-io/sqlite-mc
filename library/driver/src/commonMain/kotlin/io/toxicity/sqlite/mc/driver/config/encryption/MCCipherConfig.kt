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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.toxicity.sqlite.mc.driver.config.encryption

import io.toxicity.sqlite.mc.driver.internal.ext.appendIndent
import io.toxicity.sqlite.mc.driver.config.MCPragma
import io.toxicity.sqlite.mc.driver.config.MutableMCPragmas
import io.toxicity.sqlite.mc.driver.internal.ext.appendColon
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

public sealed class MCCipherConfig(

    /**
     * [PRAGMA cipher](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-cipher)
     * */
    @JvmField
    public val cipher: Cipher,
    @JvmField
    public val legacy: Int,
    @JvmField
    public val legacyPageSize: Int,
) {

    @JvmSynthetic
    internal fun applyPragmas(pragmas: MutableMCPragmas, addAll: Boolean = false) {
        MCPragma.CIPHER.put(pragmas, cipher)
        MCPragma.LEGACY.put(pragmas, legacy)

        when (val config = this) {
            is MCChaCha20Config -> config.apply(pragmas, addAll)
            is MCRC4Config -> config.apply(pragmas, addAll)
            is MCSqlCipherConfig -> config.apply(pragmas, addAll)
            is MCWxAES128Config -> config.apply(pragmas, addAll)
            is MCWxAES256Config -> config.apply(pragmas, addAll)
            is MCAscon128Config -> config.apply(pragmas, addAll)
        }
    }

    private fun MCChaCha20Config.apply(pragmas: MutableMCPragmas, addAll: Boolean) {
        val config = when (legacy) {
            0 -> MCChaCha20Config.Default
            else -> MCChaCha20Config.SQLeet
        }

        if (addAll || legacyPageSize != config.legacyPageSize) {
            MCPragma.LEGACY_PAGE_SIZE.put(pragmas, legacyPageSize)
        }
        if (addAll || kdfIter != config.kdfIter) {
            MCPragma.KDF_ITER.put(pragmas, kdfIter)
        }
    }
    private fun MCRC4Config.apply(pragmas: MutableMCPragmas, addAll: Boolean) {
        if (addAll || legacyPageSize != MCRC4Config.Default.legacyPageSize) {
            MCPragma.LEGACY_PAGE_SIZE.put(pragmas, legacyPageSize)
        }
    }
    private fun MCSqlCipherConfig.apply(pragmas: MutableMCPragmas, addAll: Boolean) {
        val config = when (legacy) {
            0 -> MCSqlCipherConfig.Default
            1 -> MCSqlCipherConfig.v1
            2 -> MCSqlCipherConfig.v2
            3 -> MCSqlCipherConfig.v3
            else -> MCSqlCipherConfig.v4
        }

        if (addAll || legacyPageSize != config.legacyPageSize) {
            MCPragma.LEGACY_PAGE_SIZE.put(pragmas, legacyPageSize)
        }
        if (addAll || kdfIter != config.kdfIter) {
            MCPragma.KDF_ITER.put(pragmas, kdfIter)
        }
        if (addAll || fastKdfIter != config.fastKdfIter) {
            MCPragma.FAST_KDF_ITER.put(pragmas, fastKdfIter)
        }
        if (addAll || hmacUse != config.hmacUse) {
            MCPragma.HMAC_USE.put(pragmas, hmacUse)
        }
        if (hmacPngo != null && (addAll || hmacPngo != config.hmacPngo)) {
            MCPragma.HMAC_PNGO.put(pragmas, hmacPngo)
        }
        if (hmacSaltMask != null && (addAll || hmacSaltMask != config.hmacSaltMask)) {
            MCPragma.HMAC_SALT_MASK.put(pragmas, hmacSaltMask)
        }
        if (addAll || kdfAlgorithm != config.kdfAlgorithm) {
            MCPragma.KDF_ALGORITHM.put(pragmas, kdfAlgorithm)
        }
        if (addAll || hmacAlgorithm != config.hmacAlgorithm) {
            MCPragma.HMAC_ALGORITHM.put(pragmas, hmacAlgorithm)
        }
        if (plaintextHeaderSize != null && (addAll || plaintextHeaderSize != config.plaintextHeaderSize)) {
            MCPragma.PLAIN_TEXT_HEADER_SIZE.put(pragmas, plaintextHeaderSize)
        }
    }

    private fun MCWxAES128Config.apply(pragmas: MutableMCPragmas, addAll: Boolean) {
        val config = MCWxAES128Config.Default

        if (addAll || legacyPageSize != config.legacyPageSize) {
            MCPragma.LEGACY_PAGE_SIZE.put(pragmas, legacyPageSize)
        }
    }
    private fun MCWxAES256Config.apply(pragmas: MutableMCPragmas, addAll: Boolean) {
        val config = MCWxAES256Config.Default

        if (addAll || legacyPageSize != config.legacyPageSize) {
            MCPragma.LEGACY_PAGE_SIZE.put(pragmas, legacyPageSize)
        }
        if (addAll || kdfIter != config.kdfIter) {
            MCPragma.KDF_ITER.put(pragmas, kdfIter)
        }
    }

    private fun MCAscon128Config.apply(pragmas: MutableMCPragmas, addAll: Boolean) {
        val config = MCAscon128Config.Default
        if (addAll || kdfIter != config.kdfIter) {
            MCPragma.KDF_ITER.put(pragmas, kdfIter)
        }
    }

    final override fun equals(other: Any?): Boolean {
        return when (this) {
            is MCChaCha20Config -> {
                other is MCChaCha20Config
                && other.legacy == legacy
                && other.legacyPageSize == legacyPageSize
                && other.kdfIter == kdfIter
            }
            is MCRC4Config -> {
                other is MCRC4Config
                && other.legacy == legacy
                && other.legacyPageSize == legacyPageSize
            }
            is MCSqlCipherConfig -> {
                other is MCSqlCipherConfig
                && other.legacy == legacy
                && other.legacyPageSize == legacyPageSize
                && other.kdfIter == kdfIter
                && other.fastKdfIter == fastKdfIter
                && other.hmacUse == hmacUse
                && other.hmacPngo == hmacPngo
                && other.hmacSaltMask == hmacSaltMask
                && other.kdfAlgorithm == kdfAlgorithm
                && other.hmacAlgorithm == hmacAlgorithm
                && other.plaintextHeaderSize == plaintextHeaderSize
            }
            is MCWxAES128Config -> {
                other is MCWxAES128Config
                && other.legacy == legacy
                && other.legacyPageSize == legacyPageSize
            }
            is MCWxAES256Config -> {
                other is MCWxAES256Config
                && other.legacy == legacy
                && other.legacyPageSize == legacyPageSize
                && other.kdfIter == kdfIter
            }
            is MCAscon128Config -> {
                other is MCAscon128Config
                && other.legacy == legacy
                && other.legacyPageSize == legacyPageSize
                && other.kdfIter == kdfIter
            }
        }
    }

    final override fun hashCode(): Int {
        var result = 17

        when (this) {
            is MCChaCha20Config -> {
                result = result * 31 + legacy.hashCode()
                result = result * 31 + legacyPageSize.hashCode()
                result = result * 31 + kdfIter.hashCode()
            }
            is MCRC4Config -> {
                result = result * 31 + legacy.hashCode()
                result = result * 31 + legacyPageSize.hashCode()
            }
            is MCSqlCipherConfig -> {
                result = result * 31 + legacy.hashCode()
                result = result * 31 + legacyPageSize.hashCode()
                result = result * 31 + kdfIter.hashCode()
                result = result * 31 + fastKdfIter.hashCode()
                result = result * 31 + hmacUse.hashCode()
                result = result * 31 + hmacPngo.hashCode()
                result = result * 31 + hmacSaltMask.hashCode()
                result = result * 31 + kdfAlgorithm.hashCode()
                result = result * 31 + hmacAlgorithm.hashCode()
                result = result * 31 + plaintextHeaderSize.hashCode()
            }
            is MCWxAES128Config -> {
                result = result * 31 + legacy.hashCode()
                result = result * 31 + legacyPageSize.hashCode()
            }
            is MCWxAES256Config -> {
                result = result * 31 + legacy.hashCode()
                result = result * 31 + legacyPageSize.hashCode()
                result = result * 31 + kdfIter.hashCode()
            }
            is MCAscon128Config -> {
                result = result * 31 + legacy.hashCode()
                result = result * 31 + legacyPageSize.hashCode()
                result = result * 31 + kdfIter.hashCode()
            }
        }

        return result
    }

    final override fun toString(): String {
        return buildString {
            append(this@MCCipherConfig::class.simpleName)
            appendLine(": [")
            appendIndent(MCPragma.CIPHER)
            appendColon()
            appendLine(cipher)

            when (this@MCCipherConfig) {
                is MCChaCha20Config -> {
                    appendIndent(MCPragma.LEGACY)
                    appendColon()
                    appendLine(legacy)
                    appendIndent(MCPragma.LEGACY_PAGE_SIZE)
                    appendColon()
                    appendLine(legacyPageSize)
                    appendIndent(MCPragma.KDF_ITER)
                    appendColon()
                    appendLine(kdfIter)
                }
                is MCRC4Config -> {
                    appendIndent(MCPragma.LEGACY)
                    appendColon()
                    appendLine(legacy)
                    appendIndent(MCPragma.LEGACY_PAGE_SIZE)
                    appendColon()
                    appendLine(legacyPageSize)
                }
                is MCSqlCipherConfig -> {
                    appendIndent(MCPragma.LEGACY)
                    appendColon()
                    appendLine(legacy)
                    appendIndent(MCPragma.LEGACY_PAGE_SIZE)
                    appendColon()
                    appendLine(legacyPageSize)
                    appendIndent(MCPragma.KDF_ITER)
                    appendColon()
                    appendLine(kdfIter)
                    appendIndent(MCPragma.HMAC_USE)
                    appendColon()
                    appendLine(hmacUse)
                    appendIndent(MCPragma.HMAC_PNGO)
                    appendColon()
                    append(hmacPngo)
                    if (hmacPngo != null) {
                        append(" (${hmacPngo.ordinal})")
                    }
                    appendLine()
                    appendIndent(MCPragma.HMAC_SALT_MASK)
                    appendColon()
                    appendLine(hmacSaltMask)
                    appendIndent(MCPragma.KDF_ALGORITHM)
                    appendColon()
                    append(kdfAlgorithm)
                    appendLine(" (${kdfAlgorithm.ordinal})")
                    appendIndent(MCPragma.HMAC_ALGORITHM)
                    appendColon()
                    append(hmacAlgorithm)
                    appendLine(" (${hmacAlgorithm.ordinal})")
                    appendIndent(MCPragma.PLAIN_TEXT_HEADER_SIZE)
                    appendColon()
                    appendLine(plaintextHeaderSize)
                }
                is MCWxAES128Config -> {
                    appendIndent(MCPragma.LEGACY)
                    appendColon()
                    appendLine(legacy)
                    appendIndent(MCPragma.LEGACY_PAGE_SIZE)
                    appendColon()
                    appendLine(legacyPageSize)
                }
                is MCWxAES256Config -> {
                    appendIndent(MCPragma.LEGACY)
                    appendColon()
                    appendLine(legacy)
                    appendIndent(MCPragma.LEGACY_PAGE_SIZE)
                    appendColon()
                    appendLine(legacyPageSize)
                    appendIndent(MCPragma.KDF_ITER)
                    appendColon()
                    appendLine(kdfIter)
                }
                is MCAscon128Config -> {
                    appendIndent(MCPragma.LEGACY)
                    appendColon()
                    appendLine(legacy)
                    appendIndent(MCPragma.LEGACY_PAGE_SIZE)
                    appendColon()
                    appendLine(legacyPageSize)
                    appendIndent(MCPragma.KDF_ITER)
                    appendColon()
                    appendLine(kdfIter)
                }
            }
            append(']')
        }
    }

    protected companion object {

        @Suppress("NOTHING_TO_INLINE")
        @Throws(IllegalArgumentException::class)
        internal inline fun Int.checkLegacyPageSize() {
            if (this == 0) return
            require(this in 512..65536) {
                "legacyPageSize must be 0, or within 512 and 65536"
            }
            require(65536 % this == 0) {
                "legacyPageSize must be a power of 2 (e.g. 512, 1024, 2048, 4096, 8192, ..., 65536)"
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        @Throws(IllegalArgumentException::class)
        internal inline fun Int.checkKdfIter() {
            require(this > 0) {
                "kdfIter must be greater than 0"
            }
        }
    }
}
