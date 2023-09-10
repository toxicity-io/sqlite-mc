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

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.use
import io.matthewnelson.encoding.core.util.LineBreakOutFeed
import io.toxicity.sqlite.mc.driver.internal.ext.escapeSQL
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * The encryption/decryption key used for executing
 * PRAGMA key and PRAGMA rekey statements
 *
 * [PRAGMA key](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-key)
 *
 * [PRAGMA rekey](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/#pragma-rekey)
 *
 * @see [passphrase]
 * @see [raw]
 * @see [EMPTY]
 * */
public class Key private constructor(
    private val value: String,

    @JvmField
    public val isRaw: Boolean,
) {

    public companion object {

        /**
         * An empty key which can be referenced for things like
         * Removing encryption from a database via a rekey.
         * */
        @JvmField
        public val EMPTY: Key = Key(value = "", isRaw = false)

        /**
         * A null key to use for creating in memory databases, whether
         * the factory has a FilesystemConfig declared or not.
         * */
        @JvmField
        public val IN_MEMORY: Key? = null

        /**
         * A Passphrase to encrypt/decrypt the database file with.
         * The actual encryption key will be derived from this value
         * automatically.
         *
         * e.g. "My very secret passphrase"
         * */
        @JvmStatic
        public fun passphrase(
            value: String
        ): Key = Key(value = value, isRaw = false)

        /**
         * A **RAW** key (as in you already derived a key from a
         * passphrase or something) to encrypt/decrypt the database
         * file with. [key] **Must** be 32 bytes.
         *
         * Optionally, you can also add a [salt] to be stored alongside
         * the [key]. [salt] **MUST** be 16 bytes. Pass null to not
         * append a salt.
         *
         * **NOTE:** Appending a salt *after* encrypting a database with
         * only a [key]. requires a rekey operation to be performed.
         *
         * **NOTE:** Raw keys are only supported by SQLCipher and ChaCha20
         * encryption configurations.
         *
         * @param [key] An already derived, 32 byte key
         * @param [salt] Optional 16 bytes that were used to derive [key] with
         * @param [fillKey] If true (the default), [key] bytes will be zeroed
         *   out after creating [Key] ([salt] is **not** zeroed out)
         * @throws [IllegalArgumentException] if [key] or [salt] are
         *   inappropriate sizes (32 and 16 bytes, respectively)
         * */
        @JvmStatic
        @JvmOverloads
        @Throws(IllegalArgumentException::class)
        public fun raw(
            key: ByteArray,
            salt: ByteArray?,
            fillKey: Boolean = true,
        ): Key {
            require(key.size == 32) { "A Raw key must be 32 bytes" }
            require(salt == null || salt.size == 16) { "Salt must be 16 bytes" }

            val size = key.size + (salt?.size ?: 0)
            val sb = StringBuilder(size)

            // Supplying our own LineBreakOutFeed will override the
            // default of 64 that the static Base16 instance uses.
            val out = LineBreakOutFeed(interval = Byte.MAX_VALUE, out = { c -> sb.append(c) })

            Base16.newEncoderFeed(out).use { feed ->
                key.forEach { b -> feed.consume(b) }

                if (salt != null) {
                    feed.flush()
                    salt.forEach { b -> feed.consume(b) }
                }
            }

            val k = Key(value = sb.toString(), isRaw = true)

            sb.clear()
            repeat(size) { sb.append(' ') }

            if (fillKey) key.fill(0)

            return k
        }
    }

    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    internal fun retrieveFormatted(cipher: Cipher): String {
        // passphrase
        if (!isRaw) return "'${value.escapeSQL()}'"

        return when (cipher) {
            is Cipher.SQLCIPHER -> {
                "\"x'${value}'\""
            }
            is Cipher.CHACHA20 -> {
                "'raw:${value}'"
            }
            else -> {
                throw IllegalArgumentException(
                    "Invalid key type for $cipher. " +
                    "Only ${Cipher.SQLCIPHER} and ${Cipher.CHACHA20} support raw keys."
                )
            }
        }
    }

    override fun toString(): String = "Key(value=[REDACTED], isRaw=$isRaw)"
}
