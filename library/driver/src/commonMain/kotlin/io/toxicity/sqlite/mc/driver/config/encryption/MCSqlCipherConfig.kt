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
 * Config for SQLCipher: AES 256 Bit
 *
 * @see [Builder]
 * */
public class MCSqlCipherConfig private constructor(
    legacy: Int,
    legacyPageSize: Int,
    @JvmField
    public val kdfIter: Int,
    @JvmField
    public val fastKdfIter: Int,
    @JvmField
    public val hmacUse: Boolean,
    @JvmField
    public val hmacPngo: HmacPngo?,
    @JvmField
    public val hmacSaltMask: Byte?,
    @JvmField
    public val kdfAlgorithm: KdfAlgorithm,
    @JvmField
    public val hmacAlgorithm: HmacAlgorithm,
    @JvmField
    public val plaintextHeaderSize: Int?,
): MCCipherConfig(
    cipher = Cipher.SQLCIPHER,
    legacy = legacy,
    legacyPageSize = legacyPageSize,
) {

    public companion object {

        /** Default config */
        @JvmField
        public val Default: MCSqlCipherConfig = Builder(null).apply { default() }.build()

        /** Legacy v1 compatible config */
        @JvmField
        public val v1: MCSqlCipherConfig = Builder(null).apply { v1() }.build()

        /** Legacy v2 compatible config */
        @JvmField
        public val v2: MCSqlCipherConfig = Builder(null).apply { v2() }.build()

        /** Legacy v3 compatible config */
        @JvmField
        public val v3: MCSqlCipherConfig = Builder(null).apply { v3() }.build()

        /** Legacy v4 compatible config */
        @JvmField
        public val v4: MCSqlCipherConfig = Builder(null).apply { v4() }.build()
    }

    // This forces API consumers to declare a setting to start off at before
    // customizations occur (if any) so that the [Builder] is configured
    // prior to gaining access to it.
    //
    // This helps mitigate any foot-guns that could arise out of default values
    // changing on a dependency update (new crypto or something).
    @MCCipherConfigDsl
    public class Options internal constructor(
        private val setCipher: (MCSqlCipherConfig) -> Unit,
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
         * Initial settings for v1 configuration which
         * can be further customized (if desired).
         * */
        @JvmOverloads
        @MCCipherConfigDsl
        public fun v1(block: Builder.() -> Unit = {}) {
            val cipher = Builder(Companion.v1).apply(block).build()
            setCipher(cipher)
        }

        /**
         * Initial settings for v2 configuration which
         * can be further customized (if desired).
         * */
        @JvmOverloads
        @MCCipherConfigDsl
        public fun v2(block: Builder.() -> Unit = {}) {
            val cipher = Builder(Companion.v2).apply(block).build()
            setCipher(cipher)
        }

        /**
         * Initial settings for v3 configuration which
         * can be further customized (if desired).
         * */
        @JvmOverloads
        @MCCipherConfigDsl
        public fun v3(block: Builder.() -> Unit = {}) {
            val cipher = Builder(Companion.v3).apply(block).build()
            setCipher(cipher)
        }

        /**
         * Initial settings for v4 configuration which
         * can be further customized (if desired).
         * */
        @JvmOverloads
        @MCCipherConfigDsl
        public fun v4(block: Builder.() -> Unit = {}) {
            val cipher = Builder(Companion.v4).apply(block).build()
            setCipher(cipher)
        }
    }

    /**
     * [SQLCipher: AES 256 Bit](https://utelle.github.io/SQLite3MultipleCiphers/docs/ciphers/cipher_sqlcipher/)
     *
     * @see [default]
     * @see [v1]
     * @see [v2]
     * @see [v3]
     * @see [v4]
     * */
    @MCCipherConfigDsl
    public class Builder internal constructor(other: MCSqlCipherConfig?) {

        @get:JvmName("legacy")
        public var legacy: Int = 0
            private set

        /**
         * [PRAGMA legacy](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy)
         *
         * Default: (default = 0), (v1 = 1), (v2 = 2), (v3 = 3), (v4 = 4)
         * Min: 0
         * Max: 4
         *
         * @throws [IllegalArgumentException] if not within 0 and 4
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun legacy(value: Int): Builder {
            require(value in 0..4) { "must be within 0 and 4" }
            legacy = value
            return this
        }

        @get:JvmName("legacyPageSize")
        public var legacyPageSize: Int = 4096
            private set

        /**
         * [PRAGMA legacy_page_size](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-legacy_page_size)
         *
         * Default: (default = 4096), (v1 = 1024), (v2 = 1024), (v3 = 1024), (v4 = 4096)
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
        public var kdfIter: Int = 256000
            private set

        /**
         * [PRAGMA kdf_iter](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-kdf_iter)
         *
         * Default: (default = 256000), (v1 = 4000), (v2 = 4000), (v3 = 64000), (v4 = 256000)
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

        @get:JvmName("fastKdfIter")
        public var fastKdfIter: Int = 2
            private set

        /**
         * [PRAGMA fast_kdf_iter](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-fast_kdf_iter)
         *
         * Default: (default = 2), (v1 = 2), (v2 = 2), (v3 = 2), (v4 = 2)
         * Min: 1
         * Max: 2147483647
         *
         * @throws [IllegalArgumentException] if less than 1
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun fastKdfIter(value: Int): Builder {
            value.checkKdfIter()
            fastKdfIter = value
            return this
        }

        @get:JvmName("hmacUse")
        public var hmacUse: Boolean = true
            private set

        /**
         * [PRAGMA hmac_use](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-hmac_use)
         *
         * Default: (default = true), (v1 = false), (v2 = true), (v3 = true), (v4 = true)
         * */
        @MCCipherConfigDsl
        public fun hmacUse(value: Boolean): Builder {
            hmacUse = value
            return this
        }

        @get:JvmName("hmacPngo")
        public var hmacPngo: HmacPngo? = HmacPngo.LITTLE_ENDIAN
            private set

        /**
         * [PRAGMA hmac_pgno](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-hmac_pgno)
         *
         * Default: (default = LITTLE_ENDIAN), (v1 = null), (v2 = LITTLE_ENDIAN), (v3 = LITTLE_ENDIAN), (v4 = LITTLE_ENDIAN)
         * */
        @MCCipherConfigDsl
        public fun hmacPngo(value: HmacPngo?): Builder {
            hmacPngo = value
            return this
        }

        @get:JvmName("hmacSaltMask")
        public var hmacSaltMask: Byte? = 0x3a
            private set

        /**
         * [PRAGMA hmac_salt_mask](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-hmac_salt_mask)
         *
         * Default: (default = 0x3a), (v1 = null), (v2 = 0x3a), (v3 = 0x3a), (v4 = 0x3a)
         * Min: 0
         * Max: 255
         *
         * @throws [IllegalArgumentException] if less than 0
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun hmacSaltMask(value: Byte?): Builder {
            require(value == null || value >= 0) { "must be greater than or equal to 0 (or null)" }
            hmacSaltMask = value
            return this
        }

        @get:JvmName("kdfAlgorithm")
        public var kdfAlgorithm: KdfAlgorithm = KdfAlgorithm.SHA512
            private set

        /**
         * [PRAGMA kdf_algorithm](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-kdf_algorithm)
         *
         * Default: (default = SHA512), (v1 = SHA1), (v2 = SHA1), (v3 = SHA1), (v4 = SHA512)
         * */
        @MCCipherConfigDsl
        public fun kdfAlgorithm(value: KdfAlgorithm): Builder {
            kdfAlgorithm = value
            return this
        }

        @get:JvmName("hmacAlgorithm")
        public var hmacAlgorithm: HmacAlgorithm = HmacAlgorithm.SHA512
            private set

        /**
         * [PRAGMA hmac_algorithm](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-hmac_algorithm)
         *
         * Default: (default = SHA512), (v1 = SHA1), (v2 = SHA1), (v3 = SHA1), (v4 = SHA512)
         * */
        @MCCipherConfigDsl
        public fun hmacAlgorithm(value: HmacAlgorithm): Builder {
            hmacAlgorithm = value
            return this
        }

        @get:JvmName("plaintextHeaderSize")
        public var plaintextHeaderSize: Int? = 0
            private set

        /**
         * [PRAGMA plaintext_header_size](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-plaintext_header_size)
         *
         * Default: (default = 0), (v1 = null), (v2 = null), (v3 = null), (v4 = 0)
         * Min: 0
         * Max: 100
         *
         * **NOTE:** must be multiples of 16 (e.g. 32, 48, 64...)
         *
         * @throws [IllegalArgumentException] if not within 0 and 100, or not a multiple of 16
         * */
        @MCCipherConfigDsl
        @Throws(IllegalArgumentException::class)
        public fun plaintextHeaderSize(value: Int?): Builder {
            if (value != null) {
                require(value in 0..100) { "must be between 0 and 100 (or null)" }
                require(value % 16 == 0) { "must be multiples of 16 (or null)" }
            }
            plaintextHeaderSize = value
            return this
        }

        init {
            if (other != null) {
                legacy = other.legacy
                legacyPageSize = other.legacyPageSize
                kdfIter = other.kdfIter
                fastKdfIter = other.fastKdfIter
                hmacUse = other.hmacUse
                hmacPngo = other.hmacPngo
                hmacSaltMask = other.hmacSaltMask
                kdfAlgorithm = other.kdfAlgorithm
                hmacAlgorithm = other.hmacAlgorithm
                plaintextHeaderSize = other.plaintextHeaderSize
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
            kdfIter = 256000
            fastKdfIter = 2
            hmacUse = true
            hmacPngo = HmacPngo.LITTLE_ENDIAN
            hmacSaltMask = 0x3a
            kdfAlgorithm = KdfAlgorithm.SHA512
            hmacAlgorithm = HmacAlgorithm.SHA512
            plaintextHeaderSize = 0
        }

        /**
         * Apply settings that are compatible with SQLCipher v1
         *
         * @see [MCSqlCipherConfig.v1]
         * */
        public fun v1() {
            legacy = 1
            legacyPageSize = 1024
            kdfIter = 4000
            fastKdfIter = 2
            hmacUse = false
            hmacPngo = null
            hmacSaltMask = null
            kdfAlgorithm = KdfAlgorithm.SHA1
            hmacAlgorithm = HmacAlgorithm.SHA1
            plaintextHeaderSize = null
        }

        /**
         * Apply settings that are compatible with SQLCipher v2
         *
         * @see [MCSqlCipherConfig.v2]
         * */
        public fun v2() {
            legacy = 2
            legacyPageSize = 1024
            kdfIter = 4000
            fastKdfIter = 2
            hmacUse = true
            hmacPngo = HmacPngo.LITTLE_ENDIAN
            hmacSaltMask = 0x3a
            kdfAlgorithm = KdfAlgorithm.SHA1
            hmacAlgorithm = HmacAlgorithm.SHA1
            plaintextHeaderSize = null
        }

        /**
         * Apply settings that are compatible with SQLCipher v3
         *
         * @see [MCSqlCipherConfig.v3]
         * */
        public fun v3() {
            legacy = 3
            legacyPageSize = 1024
            kdfIter = 64000
            fastKdfIter = 2
            hmacUse = true
            hmacPngo = HmacPngo.LITTLE_ENDIAN
            hmacSaltMask = 0x3a
            kdfAlgorithm = KdfAlgorithm.SHA1
            hmacAlgorithm = HmacAlgorithm.SHA1
            plaintextHeaderSize = null
        }

        /**
         * Apply settings that are compatible with SQLCipher v4
         *
         * @see [MCSqlCipherConfig.v4]
         * */
        public fun v4() {
            legacy = 4
            legacyPageSize = 4096
            kdfIter = 256000
            fastKdfIter = 2
            hmacUse = true
            hmacPngo = HmacPngo.LITTLE_ENDIAN
            hmacSaltMask = 0x3a
            kdfAlgorithm = KdfAlgorithm.SHA512
            hmacAlgorithm = HmacAlgorithm.SHA512
            plaintextHeaderSize = 0
        }

        @JvmSynthetic
        internal fun build(): MCSqlCipherConfig {
            val legacy = legacy

            return MCSqlCipherConfig(
                legacy = legacy,
                legacyPageSize = legacyPageSize,
                kdfIter = kdfIter,
                fastKdfIter = fastKdfIter,
                hmacUse = hmacUse,
                hmacPngo = if (legacy == 1) null else (hmacPngo ?: HmacPngo.LITTLE_ENDIAN),
                hmacSaltMask = if (legacy == 1) null else (hmacSaltMask ?: 0x3a),
                kdfAlgorithm = kdfAlgorithm,
                hmacAlgorithm = hmacAlgorithm,
                plaintextHeaderSize = if (legacy in 1..3) null else (plaintextHeaderSize ?: 0),
            )
        }
    }
}
