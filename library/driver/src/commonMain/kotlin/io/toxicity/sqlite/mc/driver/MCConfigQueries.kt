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
package io.toxicity.sqlite.mc.driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.toxicity.sqlite.mc.driver.config.MCPragma
import io.toxicity.sqlite.mc.driver.config.encryption.Cipher
import io.toxicity.sqlite.mc.driver.internal.ext.buildMCConfigSQL
import io.toxicity.sqlite.mc.driver.internal.ext.escapeSQL
import kotlin.jvm.JvmStatic

/**
 * [SQLite3MultipleCiphers SQL Functions](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_functions/)
 *
 * TODO: Make available. Need to also check functionality with ephemeral dbs.. might throw exceptions
 * @see [from]
 * */
internal interface MCConfigQueries {
    // TODO: documentation & tests
    fun cipher(transient: Boolean): Cipher

    // TODO: documentation & tests
    fun hmacCheck(transient: Boolean): Boolean

    // TODO: documentation & tests
    fun mcLegacyWAL(transient: Boolean): Boolean

    // TODO: documentation & tests
    fun <T: Any> cipherParam(cipher: Cipher, param: MCPragma<T>): T?

    // TODO: documentation & tests
    fun cipherSalt(schemaName: String?): String?

    // TODO: documentation & tests
    fun cipherSaltRaw(schemaName: String?): ByteArray?

    companion object {

        @JvmStatic
        fun from(
            driver: SQLiteMCDriver
        ): MCConfigQueries = MCConfigQueriesImpl(driver)
    }
}

private class MCConfigQueriesImpl(
    private val driver: SQLiteMCDriver
): MCConfigQueries {

    // TODO: QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) fix null pointers
    // TODO: If driver is closed, this should fail, so, should annotate exceptions

    override fun cipher(transient: Boolean): Cipher {
        return GetCipherQuery(transient).executeAsOne()
    }

    override fun hmacCheck(transient: Boolean): Boolean {
        return GetHmacCheckQuery(transient).executeAsOne()
    }

    override fun mcLegacyWAL(transient: Boolean): Boolean {
        return GetMCLegacyWALQuery(transient).executeAsOne()
    }

    override fun <T : Any> cipherParam(cipher: Cipher, param: MCPragma<T>): T? {
        return try {
            GetCipherParameterQuery(cipher, param).executeAsOneOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    override fun cipherSalt(schemaName: String?): String? {
        return try {
            GetCipherSaltQuery(schemaName).executeAsOneOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    override fun cipherSaltRaw(schemaName: String?): ByteArray? {
        // raw:cipher_salt is broken or something, so
        // just decode the string value.
        return cipherSalt(schemaName)?.decodeToByteArray(Base16)
    }

    private inner class GetCipherQuery(
        transient: Boolean
    ): MCConfigQueriesImpl.GetConfigQuery<Cipher>(
        paramName = MCPragma.CIPHER.name,
        optionalParamName = null,
        transient = transient,
        mapper = MCPragma.CIPHER.mapper,
    )

    private inner class GetHmacCheckQuery(
        transient: Boolean
    ): MCConfigQueriesImpl.GetConfigQuery<Boolean>(
        paramName = MCPragma.HMAC_CHECK.name,
        optionalParamName = null,
        transient = transient,
        mapper = MCPragma.HMAC_CHECK.mapper,
    )

    private inner class GetMCLegacyWALQuery(
        transient: Boolean
    ): MCConfigQueriesImpl.GetConfigQuery<Boolean>(
        paramName = MCPragma.MC_LEGACY_WAL.name,
        optionalParamName = null,
        transient = transient,
        mapper = MCPragma.MC_LEGACY_WAL.mapper,
    )

    private inner class GetCipherParameterQuery<T: Any>(
        cipher: Cipher,
        pragma: MCPragma<T>,
    ): MCConfigQueriesImpl.GetConfigQuery<T>(
        paramName = cipher.name,
        optionalParamName = pragma.name,
        transient = true,
        mapper = pragma.mapper
    )

    private inner class GetCipherSaltQuery(
        private val schemaName: String?,
    ): Query<String>(
        mapper = { it.getString(0)!! }
    ) {

        override fun addListener(listener: Listener) {
            driver.addListener("sqlite3mc_codec_data", listener = listener)
        }

        override fun removeListener(listener: Listener) {
            driver.removeListener("sqlite3mc_codec_data", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            val sql = buildString {
                append("SELECT sqlite3mc_codec_data('cipher_salt'")
                if (schemaName != null) {
                    append(", '")
                    append(schemaName.escapeSQL())
                    append('\'')
                }
                append(");")
            }

            return driver.executeQuery(null, sql, mapper, 0, null)
        }
    }

    private abstract inner class GetConfigQuery<out T: Any>
    @Throws(IllegalArgumentException::class)
    constructor(
        private val paramName: String,
        private val optionalParamName: String?,
        private val transient: Boolean,
        mapper: (SqlCursor) -> T,
    ): Query<T>(mapper) {

        init {
            when (val p = paramName) {
                MCPragma.CIPHER.name,
                MCPragma.HMAC_CHECK.name,
                MCPragma.MC_LEGACY_WAL.name -> {
                    require(optionalParamName == null) {
                        "$p cannot declare a field parameter." +
                        "That would change the setting."
                    }
                }

                MCPragma.KEY.name,
                MCPragma.RE_KEY.name -> {
                    throw IllegalArgumentException("$p does not have a config option")
                }
            }
        }

        final override fun addListener(listener: Listener) {
            driver.addListener("sqlite3mc_config", listener = listener)
        }

        final override fun removeListener(listener: Listener) {
            driver.removeListener("sqlite3mc_config", listener = listener)
        }

        final override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            val sql = paramName.buildMCConfigSQL(transient, optionalParamName, null)
            return driver.executeQuery(null, sql, mapper, 0, null)
        }
    }
}
