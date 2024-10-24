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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.toxicity.sqlite.mc.driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import io.toxicity.sqlite.mc.driver.config.*
import io.toxicity.sqlite.mc.driver.config.MutableMCPragmas
import kotlin.jvm.JvmSynthetic

public expect sealed class PlatformDriver(args: Args): SqlDriver {

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

        @JvmSynthetic
        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal fun FactoryConfig.create(keyPragma: MutableMCPragmas, rekeyPragma: MutableMCPragmas?): Args

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun FactoryConfig.create(opt: EphemeralOpt): Args

        internal class Args
    }
}
