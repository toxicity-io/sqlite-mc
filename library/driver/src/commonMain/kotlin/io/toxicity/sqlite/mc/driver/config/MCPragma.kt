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
import io.toxicity.sqlite.mc.driver.internal.ext.buildMCConfigSQL
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public abstract class MCPragma<FieldType: Any> private constructor(
    @JvmField
    public val name: String,

    @JvmSynthetic
    internal val mapper: (SqlCursor) -> FieldType,
    private val transformer: Transformer<FieldType> = TransformAny,
) {

    internal object CIPHER: MCPragma<Cipher>(
        name = "cipher",
        mapper = { Cipher.valueOfOrNull(it.getString(0)!!)!! },
        transformer = { it.name }
    )
    internal object HMAC_CHECK: MCPragma<Boolean>(
        name = "hmac_check",
        mapper = MapBoolean,
        transformer = TransformBoolean,
    )
    internal object MC_LEGACY_WAL: MCPragma<Boolean>(
        name = "mc_legacy_wal",
        mapper = MapBoolean,
        transformer = TransformBoolean,
    )

    public object LEGACY: MCPragma<Int>(
        name = "legacy",
        mapper = MapInt,
    )
    public object LEGACY_PAGE_SIZE: MCPragma<Int>(
        name = "legacy_page_size",
        mapper = MapInt,
    )
    public object KDF_ITER: MCPragma<Int>(
        name = "kdf_iter",
        mapper = MapInt,
    )
    public object FAST_KDF_ITER: MCPragma<Int>(
        name = "fast_kdf_iter",
        mapper = MapInt,
    )
    public object HMAC_USE: MCPragma<Boolean>(
        name = "hmac_use",
        mapper = MapBoolean,
        transformer = TransformBoolean,
    )
    public object HMAC_PNGO: MCPragma<HmacPngo>(
        name = "hmac_pgno",
        mapper = MapEnum(),
        transformer = TransformEnum.unsafeCast(),
    )
    public object HMAC_SALT_MASK: MCPragma<Byte>(
        name = "hmac_salt_mask",
        mapper = { MapInt(it).toByte() }
    )
    public object KDF_ALGORITHM: MCPragma<KdfAlgorithm>(
        name = "kdf_algorithm",
        mapper = MapEnum(),
        transformer = TransformEnum.unsafeCast(),
    )
    public object HMAC_ALGORITHM: MCPragma<HmacAlgorithm>(
        name = "hmac_algorithm",
        mapper = MapEnum(),
        transformer = TransformEnum.unsafeCast(),
    )
    public object PLAIN_TEXT_HEADER_SIZE: MCPragma<Int>(
        name = "plaintext_header_size",
        mapper = MapInt,
    )




    internal object KEY: MCPragma<Pair<Key, Cipher>>(
        name = "key",
        mapper = MapIllegalState.unsafeCast(),
        transformer = { (key, cipher) -> key.retrieveFormatted(cipher) },
    )
    internal object RE_KEY: MCPragma<Pair<Key, Cipher>>(
        name = "rekey",
        mapper = MapIllegalState.unsafeCast(),
        transformer = { (key, cipher) -> key.retrieveFormatted(cipher) },
    )

    override fun toString(): String = name

    @MCConfigDsl
    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    internal fun put(
        pragmas: MutableMCPragmas,
        value: FieldType,
    ) { pragmas[this] = transformer.transform(value) }

    private fun interface Transformer<in FieldType: Any> {

        @Throws(IllegalArgumentException::class)
        fun transform(value: FieldType): String
    }

    internal companion object {

        @JvmStatic
        @get:JvmSynthetic
        internal val ALL: Set<MCPragma<*>> by lazy {
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
            }
        }

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

internal typealias MutableMCPragmas = MutableMap<MCPragma<*>, String>
internal typealias MCPragmas = Map<MCPragma<*>, String>

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
internal inline fun mutableMCPragmas(): MutableMCPragmas = mutableMapOf()

@JvmSynthetic
@Throws(IllegalArgumentException::class)
internal fun MCPragmas.toMCSQLStatements(): List<String> {

    val cipher = get(MCPragma.CIPHER)?.let { Cipher.valueOf(it) }
        ?: throw IllegalArgumentException("cipher is a required parameter")
    require(containsKey(MCPragma.LEGACY)) { "legacy is a required parameter" }
    val isRekey = containsKey(MCPragma.RE_KEY)
    require(isRekey || containsKey(MCPragma.KEY)) { "key or rekey is a required parameter" }

    // If performing a rekey, this will also build
    // and add the non-transient key statements to be
    // executed **after** rekeying. It is only really
    // necessary if the cipher type is changing, but
    // including them doesn't hurt and ensures that
    // everything is executing the same every the time.
    val rekeyNonTransient = mutableListOf<String>()

    return buildList {
        if (isRekey) {
            // Must be executed before rekey statements
            // to ensure the database is decrypted.
            add("SELECT 1 FROM sqlite_schema")

            // Cannot rekey when db is in journal_mode
            // WAL, so always set to the default so that
            // any properties passed with the setting
            // will reset it to the desired value after
            // rekeying.
            add("PRAGMA journal_mode = DELETE")
        }

        for (mcPragma in MCPragma.ALL) {
            val value = get(mcPragma) ?: continue

            val sql = when (mcPragma) {
                is MCPragma.CIPHER -> {
                    if (isRekey) {
                        // e.g. SELECT sqlite3mc_config('default:cipher', 'chacha20');
                        mcPragma.name.buildMCConfigSQL(
                            transient = false,
                            arg2 = value,
                            arg3 = null
                        ).let { rekeyNonTransient.add(it) }
                    }

                    // e.g. SELECT sqlite3mc_config('cipher', 'chacha20');
                    mcPragma.name.buildMCConfigSQL(
                        transient = isRekey,
                        arg2 = value,
                        arg3 = null,
                    )
                }

                is MCPragma.KEY,
                is MCPragma.RE_KEY -> {
                    if (isRekey) {
                        "PRAGMA ${MCPragma.KEY.name} = $value".let { rekeyNonTransient.add(it) }
                    }
                    "PRAGMA ${mcPragma.name} = $value"
                }
                else -> {
                    val arg3 = value.toIntOrNull()
                        ?: throw IllegalArgumentException("wtf???")

                    if (isRekey) {
                        // e.g. SELECT sqlite3mc_config('chacha20', 'default:kdf_iter', 200_000);
                        cipher.name.buildMCConfigSQL(
                            transient = false,
                            arg2 = mcPragma.name,
                            arg3 = arg3
                        ).let { rekeyNonTransient.add(it) }
                    }

                    // e.g. SELECT sqlite3mc_config('chacha20', 'kdf_iter', 200_000);
                    cipher.name.buildMCConfigSQL(
                        transient = isRekey,
                        arg2 = mcPragma.name,
                        arg3 = arg3
                    )
                }
            }

            add(sql)
        }

        addAll(rekeyNonTransient)
    }
}
