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
import io.toxicity.sqlite.mc.driver.config.encryption.MCChaCha20Config.Companion.Default
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic

/**
 * Config for System.Data.SQLite: RC4
 *
 * @see [Builder]
 * */
public class MCRC4Config private constructor(
    legacy: Int,
    legacyPageSize: Int,
): MCCipherConfig(
    cipher = Cipher.RC4,
    legacy = legacy,
    legacyPageSize = legacyPageSize,
) {

    public companion object {

        /** Default config */
        @JvmField
        public val Default: MCRC4Config = Builder(null).apply { default() }.build()
    }

    // This forces API consumers to declare a setting to start off at before
    // customizations occur (if any) so that the [Builder] is configured
    // prior to gaining access to it.
    //
    // This helps mitigate any foot-guns that could arise out of default values
    // changing on a dependency update (new crypto or something).
    @MCCipherConfigDsl
    public class Options internal constructor(
        private val setCipher: (MCRC4Config) -> Unit,
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
     * [System.Data.SQLite: RC4]https://utelle.github.io/SQLite3MultipleCiphers/docs/ciphers/cipher_sds_rc4/)
     *
     * Nobody should use this, but it's here...
     *
     * @see [default]
     * */
    @MCCipherConfigDsl
    public class Builder internal constructor(other: MCRC4Config?) {

        /**
         * [PRAGMA legacy](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy)
         *
         * Default: 1
         * Min: 1
         * Max 1
         * */
        private val legacy: Int = 1

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
//                legacy = other.legacy
                legacyPageSize = other.legacyPageSize
            }
        }

        /**
         * Apply default settings
         *
         * @see [Default]
         * */
        public fun default() {
//            legacy = 1
            legacyPageSize = 0
        }

        @JvmSynthetic
        internal fun build(): MCRC4Config = MCRC4Config(
            legacy = legacy,
            legacyPageSize = legacyPageSize,
        )
    }
}
