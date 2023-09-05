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
@file:Suppress("SpellCheckingInspection")

package io.toxicity.sqlite.mc.driver.config.encryption

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

public sealed class Cipher private constructor(
    @JvmField
    public val name: String,
    @JvmField
    public val id: Int,
): Comparable<Cipher> {

    public object AES128CBC: Cipher(name = "aes128cbc", id = 1)
    public object AES256CBC: Cipher(name = "aes256cbc", id = 2)
    public object CHACHA20: Cipher(name = "chacha20", id = 3)
    public object SQLCIPHER: Cipher(name = "sqlcipher", id = 4)
    public object RC4: Cipher(name = "rc4", id = 5)

    final override fun toString(): String = name
    final override fun compareTo(other: Cipher): Int = id - other.id

    public companion object {

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun valueOf(name: String): Cipher = valueOfOrNull(name)
            ?: throw IllegalArgumentException("Unknown cipher name >> $name")

        @JvmStatic
        public fun valueOfOrNull(name: String): Cipher? {
            return when {
                name.equals(AES128CBC.name, ignoreCase = true) -> AES128CBC
                name.equals(AES256CBC.name, ignoreCase = true) -> AES256CBC
                name.equals(CHACHA20.name, ignoreCase = true) -> CHACHA20
                name.equals(SQLCIPHER.name, ignoreCase = true) -> SQLCIPHER
                name.equals(RC4.name, ignoreCase = true) -> RC4
                else -> null
            }
        }

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun valueOf(id: Int): Cipher = valueOfOrNull(id)
            ?: throw IllegalArgumentException("Unknown cipher id >> $id")

        @JvmStatic
        public fun valueOfOrNull(id: Int): Cipher? {
            return when (id) {
                AES128CBC.id -> AES128CBC
                AES256CBC.id -> AES256CBC
                CHACHA20.id -> CHACHA20
                SQLCIPHER.id -> SQLCIPHER
                RC4.id -> RC4
                else -> null
            }
        }
    }
}
