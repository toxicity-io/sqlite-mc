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
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic

/**
 * Config for ChaCha20-Poly1305
 *
 * @see [Builder]
 * */
public class MCChaCha20Config private constructor(
    legacy: Int,
    legacyPageSize: Int,
    @JvmField
    public val kdfIter: Int,
): MCCipherConfig(
    cipher = Cipher.CHACHA20,
    legacy = legacy,
    legacyPageSize = legacyPageSize,
) {

    public companion object {

        /** Default config */
        @JvmField
        public val Default: MCChaCha20Config = Builder(null).apply { default() }.build()

        /** Legacy SQLeet compatible config */
        @JvmField
        public val SQLeet: MCChaCha20Config = Builder(null).apply { sqleet() }.build()
    }

    // This forces API consumers to declare a setting to start off at before
    // customizations occur (if any) so that the [Builder] is configured
    // prior to gaining access to it.
    //
    // This helps mitigate any foot-guns that could arise out of default values
    // changing on a dependency update (new crypto or something).
    @MCCipherConfigDsl
    public class Options internal constructor(
        private val setCipher: (MCChaCha20Config) -> Unit
    ) {

        /**
         * Initial settings for Default configuration which
         * can be further customized (if desired).
         * */
        @JvmOverloads
        @MCCipherConfigDsl
        public fun default(block: Builder.() -> Unit = {}) {
            val cipher = Builder(Companion.Default).apply(block).build()
            setCipher(cipher)
        }

        /**
         * Initial settings for SQLeet configuration which
         * can be further customized (if desired).
         * */
        @JvmOverloads
        @MCCipherConfigDsl
        public fun sqleet(block: Builder.() -> Unit = {}) {
            val cipher = Builder(Companion.SQLeet).apply(block).build()
            setCipher(cipher)
        }
    }

    /**
     * [ChaCha20-Poly1305](https://utelle.github.io/SQLite3MultipleCiphers/docs/ciphers/cipher_chacha20/)
     *
     * @see [default]
     * @see [sqleet]
     * */
    @MCCipherConfigDsl
    public class Builder internal constructor(other: MCChaCha20Config?) {

        @get:JvmName("legacy")
        public var legacy: Int = 0
            private set

        /**
         * [PRAGMA legacy](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy)
         *
         * Default: (default = 0), (sqleet = 1)
         * Min: 0
         * Max: 1
         *
         * @throws [IllegalArgumentException] if not within 0 and 1
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun legacy(value: Int): Builder {
            require(value in 0..1) { "must be with 0 (default) and 1 (sqleet)" }
            legacy = value
            return this
        }

        @get:JvmName("legacyPageSize")
        public var legacyPageSize: Int = 4096
            private set

        /**
         * [PRAGMA legacy_page_size](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy_page_size)
         *
         * Default: (default = 4096), (sqleet = 4096)
         * Min: 0
         * Max: 65536
         *
         * **NOTE:** must be powers of 2 (e.g. 512, 1024, 4096, 8192, ...)
         *
         * @throws [IllegalArgumentException] if not 0, not within 512 and 65536, else not a power of 2
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun legacyPageSize(value: Int): Builder {
            value.checkLegacyPageSize()
            legacyPageSize = value
            return this
        }

        @get:JvmName("kdfIter")
        public var kdfIter: Int = 64007
            private set

        /**
         * [PRAGMA kdf_iter](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-kdf_iter)
         *
         * Default: (default = 64007), (sqleet = 12345)
         * Min: 1
         * Max: 2147483647
         *
         * @throws [IllegalArgumentException] if less than 1
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun kdfIter(value: Int): Builder {
            value.checkKdfIter()
            kdfIter = value
            return this
        }

        init {
            if (other != null) {
                legacy = other.legacy
                legacyPageSize = other.legacyPageSize
                kdfIter = other.kdfIter
            }
        }

        /**
         * Apply default settings
         *
         * @see [Default]
         * */
        public fun default() {
            legacy = 0
            legacyPageSize = 4096
            kdfIter = 64007
        }

        /**
         * Apply settings that are compatible with sqleet
         *
         * @see [SQLeet]
         * */
        public fun sqleet() {
            legacy = 1
            legacyPageSize = 4096
            kdfIter = 12345
        }

        @JvmSynthetic
        internal fun build(): MCChaCha20Config = MCChaCha20Config(
            legacy = legacy,
            legacyPageSize = legacyPageSize,
            kdfIter = kdfIter,
        )
    }
}
