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
 * Config for Ascon: Ascon-128 v1.2
 *
 * @see [Builder]
 * */
public class MCAscon128Config private constructor(
    legacy: Int,
    legacyPageSize: Int,
    @JvmField
    public val kdfIter: Int,
): MCCipherConfig(
    cipher = Cipher.ASCON128,
    legacy = legacy,
    legacyPageSize = legacyPageSize,
) {

    public companion object {

        /** Default config */
        @JvmField
        public val Default: MCAscon128Config = Builder(null).apply { default() }.build()
    }

    // This forces API consumers to declare a setting to start off at before
    // customizations occur (if any) so that the [Builder] is configured
    // prior to gaining access to it.
    //
    // This helps mitigate any foot-guns that could arise out of default values
    // changing on a dependency update (new crypto or something).
    @MCCipherConfigDsl
    public class Options internal constructor(
        private val setCipher: (MCAscon128Config) -> Unit,
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
     * [Ascon: Ascon-128 v1.2](https://utelle.github.io/SQLite3MultipleCiphers/docs/ciphers/cipher_ascon/)
     *
     * @see [default]
     * */
    @MCCipherConfigDsl
    public class Builder internal constructor(other: MCAscon128Config?) {

        /**
         * [PRAGMA legacy](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy)
         *
         * Default: 0
         * Min: 0
         * Max 0
         * */
        private val legacy: Int = 0

        /**
         * [PRAGMA legacy_page_size](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy_page_size)
         *
         * Default: 4096
         * Min: 0
         * Max: 65536
         *
         * **NOTE:** must be powers of 2 (e.g. 512, 1024, 4096, 8192, ...)
         * */
        private val legacyPageSize: Int = 4096


        @get:JvmName("kdfIter")
        public var kdfIter: Int = 64007
            private set

        /**
         * [PRAGMA kdf_iter](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-kdf_iter)
         *
         * Default: 64007
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
                kdfIter = other.kdfIter
            }
        }
        /**
         * Apply default settings
         *
         * @see [Default]
         * */
        public fun default() {
            kdfIter = 64007
        }

        @JvmSynthetic
        internal fun build(): MCAscon128Config = MCAscon128Config(
            legacy = legacy,
            legacyPageSize = legacyPageSize,
            kdfIter = kdfIter,
        )
    }
}
