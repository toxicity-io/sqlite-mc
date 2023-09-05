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
@file:Suppress("ClassName", "SpellCheckingInspection")

package io.toxicity.sqlite.mc.driver.config

import app.cash.sqldelight.db.SqlCursor
import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.config.encryption.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public abstract class Pragma<FieldType: Any> private constructor(
    @JvmField
    public val name: String,

    private val transformer: Transformer<FieldType>
) {

    public abstract class MC<FieldType: Any> private constructor(
        name: String,
        transformer: Transformer<FieldType> = TransformAny.unsafeCast(),

        @JvmSynthetic
        internal val mapper: (SqlCursor) -> FieldType
    ): Pragma<FieldType>(name, transformer) {

        internal object CIPHER: MC<Cipher>(
            name = "cipher",
            transformer = { it.name },
            mapper = { Cipher.valueOfOrNull(it.getString(0)!!)!! }
        )
        internal object HMAC_CHECK: MC<Boolean>(
            name = "hmac_check",
            transformer = TransformBoolean,
            mapper = MapBoolean,
        )
        internal object MC_LEGACY_WAL: MC<Boolean>(
            name = "mc_legacy_wal",
            transformer = TransformBoolean,
            mapper = MapBoolean,
        )

        public object LEGACY: MC<Int>(
            name = "legacy",
            mapper = MapInt,
        )
        public object LEGACY_PAGE_SIZE: MC<Int>(
            name = "legacy_page_size",
            mapper = MapInt,
        )
        public object KDF_ITER: MC<Int>(
            name = "kdf_iter",
            mapper = MapInt,
        )
        public object FAST_KDF_ITER: MC<Int>(
            name = "fast_kdf_iter",
            mapper = MapInt,
        )
        public object HMAC_USE: MC<Boolean>(
            name = "hmac_use",
            transformer = TransformBoolean,
            mapper = MapBoolean,
        )
        public object HMAC_PNGO: MC<HmacPngo>(
            name = "hmac_pgno",
            transformer = TransformEnum.unsafeCast(),
            mapper = MapEnum(),
        )
        public object HMAC_SALT_MASK: MC<Byte>(
            name = "hmac_salt_mask",
            mapper = { MapInt(it).toByte() }
        )
        public object KDF_ALGORITHM: MC<KdfAlgorithm>(
            name = "kdf_algorithm",
            transformer = TransformEnum.unsafeCast(),
            mapper = MapEnum(),
        )
        public object HMAC_ALGORITHM: MC<HmacAlgorithm>(
            name = "hmac_algorithm",
            transformer = TransformEnum.unsafeCast(),
            mapper = MapEnum(),
        )
        public object PLAIN_TEXT_HEADER_SIZE: MC<Int>(
            name = "plaintext_header_size",
            mapper = MapInt,
        )




        internal object KEY: MC<Pair<Key, Cipher>>(
            name = "key",
            transformer = { (key, cipher) -> key.retrieve(cipher, isReKey = false) },
            mapper = MapIllegalState.unsafeCast(),
        )
        internal object RE_KEY: MC<Pair<Key, Cipher>>(
            name = "rekey",
            // performing rekey should not add the key to list of pragmas,
            // as the mc_config interface is used to set parameters and the
            // PRAGMA rekey = 'xxxxx'; statement is executed separately.
            transformer = { throw IllegalArgumentException("Use key.retrieve") },
            mapper = MapIllegalState.unsafeCast(),
        )

        // Needed for when a raw key is passed for SQLCipher
        // such that JDBC knows to format it in line with
        // what is expected >> "x'<key><salt??>'"
        //
        // Otherwise, JDBC will execute it as any other passphrase
        // and wrap in '' (which will fudge thigs).
        internal object JDBC_HEXKEY_MODE_SQLCIPHER: MC<Unit>(
            name = "hexkey_mode",
            transformer = { "SQLCIPHER" },
            mapper = MapIllegalState.unsafeCast(),
        )

        internal companion object {

            @JvmStatic
            @get:JvmSynthetic
            internal val ALL: Set<MC<*>> by lazy {
                buildSet(capacity = 16) {
                    add(CIPHER)
                    add(HMAC_CHECK)
                    add(MC_LEGACY_WAL)
                    add(LEGACY)
                    add(LEGACY_PAGE_SIZE)
                    add(KDF_ITER)
                    add(FAST_KDF_ITER)
                    add(HMAC_USE)
                    add(HMAC_PNGO)
                    add(HMAC_SALT_MASK)
                    add(KDF_ALGORITHM)
                    add(HMAC_ALGORITHM)
                    add(PLAIN_TEXT_HEADER_SIZE)
                    add(KEY)
                    add(RE_KEY)
                    add(JDBC_HEXKEY_MODE_SQLCIPHER)
                }
            }
        }
    }

    override fun toString(): String = name

    @MCConfigDsl
    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    internal fun put(
        pragmas: MutablePragmas,
        value: FieldType,
    ) { pragmas[this] = transformer.transform(value) }

    private fun interface Transformer<in FieldType: Any> {

        @Throws(IllegalArgumentException::class)
        fun transform(value: FieldType): String
    }

    private companion object {
        private val TransformAny: Transformer<Any> = Transformer { it.toString() }
        private val TransformBoolean: Transformer<Boolean> = Transformer { (if (it) 1 else 0).toString() }
        private val TransformEnum: Transformer<Enum<*>> = Transformer { it.ordinal.toString() }

        private val MapBoolean: (SqlCursor) -> Boolean = { it.getLong(0)!! == 1L }
        private val MapInt: (SqlCursor) -> Int = { it.getLong(0)!!.toInt() }
        private val MapIllegalState: (SqlCursor) -> Any = {
            throw IllegalStateException("This parameter has no config option")
        }

        @Suppress("FunctionName")
        private inline fun <reified T: Enum<T>> MapEnum(): (SqlCursor) -> T = { cursor ->
            val ordinal = cursor.getLong(0)!!.toInt()
            enumValues<T>().elementAtOrNull(ordinal)!!
        }

        @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
        private inline fun <FieldType: Any, T: Transformer<*>> T.unsafeCast(): Transformer<FieldType> {
            return this as Transformer<FieldType>
        }

        @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
        private inline fun <FieldType: Any, S: (SqlCursor) -> Any> S.unsafeCast(): (SqlCursor) -> FieldType {
            return this as (SqlCursor) -> FieldType
        }
    }
}

internal typealias MutablePragmas = MutableMap<Pragma<*>, String>
internal typealias Pragmas = Map<Pragma<*>, String>

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
internal inline fun mutablePragmas(): MutablePragmas = mutableMapOf()

@JvmSynthetic
internal fun MutablePragmas.removeMCPragmas() {
    Pragma.MC.ALL.forEach { remove(it) }
}
