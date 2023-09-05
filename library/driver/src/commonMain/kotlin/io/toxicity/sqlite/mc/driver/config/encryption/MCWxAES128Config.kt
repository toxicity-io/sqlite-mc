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
 * Config for wxSQLite3: AES 128 Bit
 *
 * @see [Builder]
 * */
public class MCWxAES128Config private constructor(
    legacy: Int,
    legacyPageSize: Int,
): MCCipherConfig(
    cipher = Cipher.AES128CBC,
    legacy = legacy,
    legacyPageSize = legacyPageSize,
) {

    public companion object {

        /** Default config */
        @JvmField
        public val Default: MCWxAES128Config = Builder(null).apply { default() }.build()
    }

    // This forces API consumers to declare a setting to start off at before
    // customizations occur (if any) so that the [Builder] is configured
    // prior to gaining access to it.
    //
    // This helps mitigate any foot-guns that could arise out of default values
    // changing on a dependency update (new crypto or something).
    @MCCipherConfigDsl
    public class Options internal constructor(
        private val setCipher: (MCWxAES128Config) -> Unit,
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
    }

    /**
     * [wxSQLite3: AES 128 Bit](https://utelle.github.io/SQLite3MultipleCiphers/docs/ciphers/cipher_aes128cbc/)
     *
     * @see [default]
     * */
    @MCCipherConfigDsl
    public class Builder internal constructor(other: MCWxAES128Config?) {

        @get:JvmName("legacy")
        public var legacy: Int = 0
            private set

        /**
         * [PRAGMA legacy](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy)
         *
         * Default: 0 (false)
         * Min: 0 (false)
         * Max: 1 (true)
         *
         * @throws [IllegalArgumentException] if not within 0 and 1
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun legacy(value: Int): Builder {
            require(value in 0..1) { "must be 0 (false) or 1 (true)" }
            legacy = value
            return this
        }

        @get:JvmName("legacyPageSize")
        public var legacyPageSize: Int = 0
            private set

        /**
         * [PRAGMA legacy_page_size](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy_page_size)
         *
         * Default: 0
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

        init {
            if (other != null) {
                legacy = other.legacy
                legacyPageSize = other.legacyPageSize
            }
        }

        /**
         * Apply default settings
         *
         * @see [Default]
         * */
        public fun default() {
            legacy = 0
            legacyPageSize = 0
        }

        @JvmSynthetic
        internal fun build(): MCWxAES128Config = MCWxAES128Config(
            legacy = legacy,
            legacyPageSize = legacyPageSize,
        )
    }
}
