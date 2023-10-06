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

import io.toxicity.sqlite.mc.driver.MCCipherConfigDsl
import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.internal.ext.appendIndent
import io.toxicity.sqlite.mc.driver.config.FilesystemConfig
import io.toxicity.sqlite.mc.driver.config.MCPragma
import io.toxicity.sqlite.mc.driver.config.MutableMCPragmas
import io.toxicity.sqlite.mc.driver.internal.ext.appendColon
import kotlin.jvm.*

/**
 * A Config that, when declared, will apply encryption to the
 * database file.
 *
 * If omitted from the [FilesystemConfig], database files will
 * not be encrypted.
 *
 * @see [FilesystemConfig.Builder.encryption]
 * @see [Builder]
 * */
public class EncryptionConfig private constructor(
    @JvmField
    public val cipherConfig: MCCipherConfig,
    @JvmField
    public val hmacCheck: Boolean,
    @JvmField
    public val mcLegacyWAL: Boolean,
) {

    /**
     * Create a new configuration based off of this one.
     *
     * @throws [IllegalStateException] if a cipher was not selected.
     * */
    @Throws(IllegalStateException::class)
    public fun new(
        block: Builder.() -> Unit,
    ): EncryptionConfig = newOrNull(block)
        ?: throw IllegalStateException("No cipher was configured")

    /**
     * Create a new configuration based off of this one.
     * */
    public fun newOrNull(
        block: Builder.() -> Unit,
    ): EncryptionConfig? = newOrNull(this, block)

    public companion object {

        /**
         * A default encryption config that utilizes [MCChaCha20Config.Default]
         * as its encryption scheme.
         * */
        @JvmField
        public val Default: EncryptionConfig = new(null) { chaCha20(MCChaCha20Config.Default) }

        /**
         * Helper for creating a new configuration to share
         * across different [SQLiteMCDriver.Factory] or [FilesystemConfig]
         *
         * @param [other] another configuration to inherit from.
         * @throws [IllegalStateException] if a cipher was not selected.
         * */
        @JvmStatic
        @Throws(IllegalStateException::class)
        public fun new(
            other: EncryptionConfig?,
            block: Builder.() -> Unit,
        ): EncryptionConfig = newOrNull(other, block)
            ?: throw IllegalStateException("No cipher was configured")

        /**
         * Helper for creating a new configuration to share
         * across different [SQLiteMCDriver.Factory]s or [FilesystemConfig]s
         *
         * @param [other] another configuration to inherit from.
         * */
        @JvmStatic
        public fun newOrNull(
            other: EncryptionConfig?,
            block: Builder.() -> Unit,
        ): EncryptionConfig? = Builder(other).apply(block).build()
    }

    /**
     * Configure encryption.
     *
     * **NOTE:** An [MCCipherConfig] MUST be configured in order
     * to add an [EncryptionConfig].
     *
     * [Builder.build] will return null if an [MCCipherConfig] is
     * not selected.
     *
     * e.g.
     *
     *     filesystem {
     *         encryption {
     *             // Nothing selected
     *         }
     *     }
     *
     *     filesystem {
     *         encryption {
     *             chaCha20 {
     *                 // Nothing selected
     *             }
     *         }
     *     }
     *
     *     filesystem {
     *         encryption {
     *             chaCha20 {
     *                 // Something selected :+1:
     *                 default()
     *             }
     *         }
     *     }
     *
     * Configure encryption to be applied.
     *
     * e.g.
     *
     *     filesystem {
     *         encryption {
     *             sqlCipher {
     *                 // Use default settings
     *                 default()
     *
     *                 // Use settings compatible with SQLCipher Version 4
     *                 // (e.g. migrating project from SQLCipher library)
     *                 v4()
     *
     *                 // Will override all above configs as it
     *                 // comes last (only one EncryptionConfig
     *                 // per FilesystemConfig).
     *                 default {
     *
     *                     // Deviate from defaults and customize
     *                     // things.
     *                     //
     *                     // WARNING: Be careful modifying this.
     *                     // If you change it to something else
     *                     // in a later update (without a proper
     *                     // migration strategy), the database
     *                     // will fail to decrypt even if the same
     *                     // password was used.
     *                     kdfIters = 350_000
     *                 }
     *             }
     *         }
     *     }
     *
     * See >> [Cipher Overview](https://utelle.github.io/SQLite3MultipleCiphers/docs/ciphers/cipher_overview/)
     * */
    @MCConfigDsl
    public open class Builder internal constructor(other: EncryptionConfig?) {

        private var cipherConfig: MCCipherConfig? = null

        @get:JvmName("hmacCheck")
        public var hmacCheck: Boolean = true
            private set

        /**
         * [PRAGMA hmac_check](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-hmac_check)
         * */
        @MCCipherConfigDsl
        public fun hmacCheck(value: Boolean): Builder {
            hmacCheck = value
            return this
        }

        @get:JvmName("mcLegacyWAL")
        public var mcLegacyWAL: Boolean = false
            private set

        /**
         * [PRAGMA mc_legacy_wal](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-mc_legacy_wal)
         * */
        @MCCipherConfigDsl
        public fun mcLegacyWAL(value: Boolean): Builder {
            mcLegacyWAL = value
            return this
        }

        init {
            if (other != null) {
                cipherConfig = other.cipherConfig
                hmacCheck = other.hmacCheck
                mcLegacyWAL = other.mcLegacyWAL
            }
        }

        @MCConfigDsl
        public fun chaCha20(
            block: MCChaCha20Config.Options.() -> Unit,
        ): Builder {
            MCChaCha20Config.Options { cipherConfig = it }.apply(block)
            return this
        }

        @MCConfigDsl
        @JvmOverloads
        public fun chaCha20(
            other: MCChaCha20Config,
            block: MCChaCha20Config.Builder.() -> Unit = {},
        ): Builder {
            cipherConfig = MCChaCha20Config.Builder(other).apply(block).build()
            return this
        }

        @MCConfigDsl
        public fun rc4(
            block: MCRC4Config.Options.() -> Unit,
        ): Builder {
            MCRC4Config.Options { cipherConfig = it }.apply(block)
            return this
        }

        @MCConfigDsl
        @JvmOverloads
        public fun rc4(
            other: MCRC4Config,
            block: MCRC4Config.Builder.() -> Unit = {},
        ): Builder {
            cipherConfig = MCRC4Config.Builder(other).apply(block).build()
            return this
        }

        @MCConfigDsl
        public fun sqlCipher(
            block: MCSqlCipherConfig.Options.() -> Unit
        ): Builder {
            MCSqlCipherConfig.Options { cipherConfig = it }.apply(block)
            return this
        }

        @MCConfigDsl
        @JvmOverloads
        public fun sqlCipher(
            other: MCSqlCipherConfig,
            block: MCSqlCipherConfig.Builder.() -> Unit = {},
        ): Builder {
            cipherConfig = MCSqlCipherConfig.Builder(other).apply(block).build()
            return this
        }

        @MCConfigDsl
        public fun wxAES128(
            block: MCWxAES128Config.Options.() -> Unit,
        ): Builder {
            MCWxAES128Config.Options { cipherConfig = it }.apply(block)
            return this
        }

        @MCConfigDsl
        @JvmOverloads
        public fun wxAES128(
            other: MCWxAES128Config,
            block: MCWxAES128Config.Builder.() -> Unit = {},
        ): Builder {
            cipherConfig = MCWxAES128Config.Builder(other).apply(block).build()
            return this
        }

        @MCConfigDsl
        public fun wxAES256(
            block: MCWxAES256Config.Options.() -> Unit
        ): Builder {
            MCWxAES256Config.Options { cipherConfig = it }.apply(block)
            return this
        }

        @MCConfigDsl
        @JvmOverloads
        public fun wxAES256(
            other: MCWxAES256Config,
            block: MCWxAES256Config.Builder.() -> Unit = {},
        ): Builder {
            cipherConfig = MCWxAES256Config.Builder(other).apply(block).build()
            return this
        }

        @JvmSynthetic
        internal fun build(): EncryptionConfig? = cipherConfig?.let {
            EncryptionConfig(
                cipherConfig = it,
                hmacCheck = hmacCheck,
                mcLegacyWAL = mcLegacyWAL,
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        return  other is EncryptionConfig
                && other.hmacCheck == hmacCheck
                && other.mcLegacyWAL == mcLegacyWAL
                && other.cipherConfig == cipherConfig
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 31 + hmacCheck.hashCode()
        result = result * 31 + mcLegacyWAL.hashCode()
        result = result * 31 + cipherConfig.hashCode()
        return result
    }

    override fun toString(): String {
        return buildString {
            append(this@EncryptionConfig::class.simpleName)
            appendLine(": [")
            appendIndent(MCPragma.HMAC_CHECK)
            appendColon()
            appendLine(hmacCheck)
            appendIndent(MCPragma.MC_LEGACY_WAL)
            appendColon()
            appendLine(mcLegacyWAL)

            appendIndent("cipherConfig: [")
            appendLine()

            val lines = cipherConfig.toString().lines()

            for (i in 1..lines.lastIndex) {
                appendIndent(lines[i])
                appendLine()
            }

            append(']')
        }
    }

    @JvmSynthetic
    internal fun applyPragmas(pragmas: MutableMCPragmas, addAll: Boolean = false): EncryptionConfig {
        if (!pragmas.containsKey(MCPragma.CIPHER)) {
            cipherConfig.applyPragmas(pragmas, addAll)
        }
        if (addAll || !hmacCheck) {
            MCPragma.HMAC_CHECK.put(pragmas, hmacCheck)
        }
        if (addAll || mcLegacyWAL) {
            MCPragma.MC_LEGACY_WAL.put(pragmas, mcLegacyWAL)
        }
        return this
    }

    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    internal fun applyKeyPragma(pragmas: MutableMCPragmas, key: Key, isRekey: Boolean): EncryptionConfig {
        if (isRekey) {
            MCPragma.RE_KEY.put(pragmas, Pair(key, cipherConfig.cipher))
        } else {
            MCPragma.KEY.put(pragmas, Pair(key, cipherConfig.cipher))
        }
        return this
    }
}
