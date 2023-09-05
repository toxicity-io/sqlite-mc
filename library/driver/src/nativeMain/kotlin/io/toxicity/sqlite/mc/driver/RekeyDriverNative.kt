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
import io.toxicity.sqlite.mc.driver.config.FactoryConfig
import io.toxicity.sqlite.mc.driver.config.Pragmas
import io.toxicity.sqlite.mc.driver.config.encryption.EncryptionConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key

public actual sealed class RekeyDriver actual constructor(args: Args): SqlDriver {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected actual fun rekey(key: Key, config: EncryptionConfig) {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun notifyListeners(vararg queryKeys: String) {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun currentTransaction(): Transacter.Transaction? {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun newTransaction(): QueryResult<Transacter.Transaction> {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> {
        throw IllegalStateException("Not yet implemented")
    }

    actual final override fun close() {
        throw IllegalStateException("Not yet implemented")
    }
    
    protected actual companion object {
        
        @Throws(IllegalStateException::class)
        internal actual fun FactoryConfig.create(pragmas: Pragmas, isInMemory: Boolean): Args {
            throw IllegalStateException("Not yet implemented")
        }

        internal actual class Args
    }
}
