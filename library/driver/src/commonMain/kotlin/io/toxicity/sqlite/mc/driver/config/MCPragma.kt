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
import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableList
import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.config.encryption.*
import kotlin.jvm.JvmField
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
        transformer = TransformEnum,
    )
    public object HMAC_SALT_MASK: MCPragma<Byte>(
        name = "hmac_salt_mask",
        mapper = { MapInt(it).toByte() }
    )
    public object KDF_ALGORITHM: MCPragma<KdfAlgorithm>(
        name = "kdf_algorithm",
        mapper = MapEnum(),
        transformer = TransformEnum,
    )
    public object HMAC_ALGORITHM: MCPragma<HmacAlgorithm>(
        name = "hmac_algorithm",
        mapper = MapEnum(),
        transformer = TransformEnum,
    )
    public object PLAIN_TEXT_HEADER_SIZE: MCPragma<Int>(
        name = "plaintext_header_size",
        mapper = MapInt,
    )




    internal object KEY: MCPragma<Pair<Key, Cipher>>(
        name = "key",
        mapper = { throw IllegalStateException("This parameter has no config option") },
        transformer = { (key, cipher) -> key.retrieveFormatted(cipher) },
    )
    internal object RE_KEY: MCPragma<Pair<Key, Cipher>>(
        name = "rekey",
        mapper = { throw IllegalStateException("This parameter has no config option") },
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

        @get:JvmSynthetic
        internal val ALL: Set<MCPragma<*>> by lazy {
            immutableSetOf(
                CIPHER,
                HMAC_CHECK,
                MC_LEGACY_WAL,
                LEGACY,
                LEGACY_PAGE_SIZE,
                KDF_ITER,
                FAST_KDF_ITER,
                HMAC_USE,
                HMAC_PNGO,
                HMAC_SALT_MASK,
                KDF_ALGORITHM,
                HMAC_ALGORITHM,
                PLAIN_TEXT_HEADER_SIZE,
                KEY,
                RE_KEY,
            )
        }

        private val TransformAny: Transformer<Any> = Transformer { it.toString() }
        private val TransformBoolean: Transformer<Boolean> = Transformer { (if (it) 1 else 0).toString() }
        private val TransformEnum: Transformer<Enum<*>> = Transformer { it.ordinal.toString() }

        private val MapBoolean: (SqlCursor) -> Boolean = { it.getLong(0)!! == 1L }
        private val MapInt: (SqlCursor) -> Int = { it.getLong(0)!!.toInt() }

        @Suppress("FunctionName")
        private inline fun <reified T: Enum<T>> MapEnum(): (SqlCursor) -> T = { cursor ->
            val ordinal = cursor.getLong(0)!!.toInt()
            enumValues<T>().elementAtOrNull(ordinal)!!
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

    get(MCPragma.CIPHER)?.let { Cipher.valueOf(it) }
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
    val rekeyNonTransient = ArrayList<String>(if (isRekey) 2 + size else 0)

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

        val schemaName = if (isRekey) "temp" else "main"

        for (mcPragma in MCPragma.ALL) {
            val value = get(mcPragma) ?: continue

            val sql = when (mcPragma) {
                is MCPragma.CIPHER -> {
                    if (isRekey) {
                        "PRAGMA main.${mcPragma.name} = $value".let { rekeyNonTransient.add(it) }
                    }
                    "PRAGMA $schemaName.${mcPragma.name} = $value"
                }

                is MCPragma.KEY,
                is MCPragma.RE_KEY -> {
                    if (isRekey) {
                        "PRAGMA ${MCPragma.KEY.name} = $value".let { rekeyNonTransient.add(it) }
                    }
                    "PRAGMA ${mcPragma.name} = $value"
                }
                else -> {
                    val integer = value.toIntOrNull() ?: throw IllegalArgumentException("wtf???")
                    if (isRekey) {
                        "PRAGMA main.${mcPragma.name} = $integer".let { rekeyNonTransient.add(it) }
                    }
                    "PRAGMA $schemaName.${mcPragma.name} = $integer"
                }
            }

            add(sql)
        }

        addAll(rekeyNonTransient)
    }.toImmutableList()
}
