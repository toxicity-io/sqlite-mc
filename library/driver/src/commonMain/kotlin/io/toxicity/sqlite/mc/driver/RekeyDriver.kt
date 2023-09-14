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
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import io.toxicity.sqlite.mc.driver.config.*
import io.toxicity.sqlite.mc.driver.config.Pragmas
import io.toxicity.sqlite.mc.driver.config.encryption.Cipher
import io.toxicity.sqlite.mc.driver.config.encryption.EncryptionConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import io.toxicity.sqlite.mc.driver.config.mutablePragmas
import io.toxicity.sqlite.mc.driver.internal.ext.buildMCConfigSQL
import io.toxicity.sqlite.mc.driver.internal.ext.escapeSQL
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public expect sealed class RekeyDriver(args: Args): SqlDriver {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected fun rekey(key: Key, config: EncryptionConfig)

    final override fun addListener(vararg queryKeys: String, listener: Query.Listener)
    final override fun notifyListeners(vararg queryKeys: String)
    final override fun removeListener(vararg queryKeys: String, listener: Query.Listener)

    final override fun currentTransaction(): Transacter.Transaction?
    final override fun newTransaction(): QueryResult<Transacter.Transaction>
    final override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long>

    final override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R>

    final override fun close()

    protected companion object {

        @JvmStatic
        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun FactoryConfig.create(pragmas: Pragmas, isInMemory: Boolean): Args

        internal class Args
    }
}

internal data class RekeyParameters(
    internal val pragmas: MutablePragmas,
    internal val sqlStatements: List<String>,
)

@JvmSynthetic
@Throws(IllegalArgumentException::class, IllegalStateException::class)
internal fun EncryptionConfig.createRekeyParameters(
    key: Key,
    isInMemory: Boolean
): RekeyParameters {
    check(!isInMemory) { "Unable to rekey. In Memory databases do not use encryption" }

    val pragmaRekeySQL = "PRAGMA ${Pragma.MC.RE_KEY.name} = ${key.retrieveFormatted(cipherConfig.cipher)};"

    val pragmas = mutablePragmas()
    applyPragmas(pragmas)

    val sqlStatements = buildList {
        pragmas.forEach { entry ->
            val sql = if (entry.key is Pragma.MC.CIPHER) {
                // e.g. SELECT sqlite3mc_config('cipher', 'chacha20');
                entry.key.name.buildMCConfigSQL(
                    transient = true,
                    arg2 = entry.value,
                    arg3 = null,
                )
            } else {
                // e.g. SELECT sqlite3mc_config('chacha20', 'kdf_iter', 200_000);
                cipherConfig.cipher.name.buildMCConfigSQL(
                    transient = true,
                    arg2 = entry.key.name,
                    arg3 = entry.value.toIntOrNull()
                        ?: throw IllegalStateException("wtf??")
                )
            }

            add(sql)
        }

        add(pragmaRekeySQL)
    }

    applyKeyPragma(pragmas, key)

    return RekeyParameters(pragmas, sqlStatements)
}
