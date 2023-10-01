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
package io.toxicity.sqlite.mc.driver.internal

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.encoding.core.use
import io.toxicity.sqlite.mc.driver.config.pragma.Pragma
import org.sqlite.JDBC
import org.sqlite.jdbc4.JDBC4Connection
import org.sqlite.jdbc4.JDBC4Statement
import java.security.SecureRandom
import java.sql.SQLException
import java.sql.Statement
import java.util.Properties

internal class JDBCMC4Connection private constructor(
    url: String,
    fileName: String,
    prop: Properties,
): JDBC4Connection(url, fileName, prop) {

    override fun createStatement(rst: Int, rsc: Int, rsh: Int): Statement {
        checkOpen()
        checkCursor(rst, rsc, rsh)

        return JDBCMC4Statement(this)
    }

    internal companion object {

        @JvmStatic
        @JvmSynthetic
        @Throws(SQLException::class)
        internal fun of(
            url: String?,
            prop: JDBCMCProperties,
        ): JDBCMC4Connection? {
            if (!JDBC.isValidURL(url)) return null
            val trimmed = url?.trim()?.plus(prop.ephemeralUrlSuffix) ?: return null

            val fileName = trimmed.substring(JDBCMC.PREFIX.length)

            return prop.withKeyProps(JDBCMC4Statement.KEY_IDENTIFIER) { keyProp ->
                JDBCMC4Connection(trimmed, fileName, keyProp)
            }
        }
    }
}

private class JDBCMC4Statement(connection: JDBCMC4Connection): JDBC4Statement(connection) {

    companion object {
        // 64 character random id which is used to ensure
        // any encoded PRAGMA key execution values are from
        // JDBCMCConnection. Will always be stripped and
        // removed upon execute invocation.
        //
        // MUST be 64 chars max, as that is the
        // line break interval set for the encoder.
        val KEY_IDENTIFIER: String by lazy {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            bytes.encodeToString(Base16)
        }
    }

    override fun execute(sql: String?): Boolean {
        if (sql == null || !sql.startsWith("pragma ${Pragma.MC.KEY.name} =", ignoreCase = true)) {
            return super.execute(sql)
        }

        // pragma key = 'KEY_IDENTIFIERbase64.encoded.cipher.statements.'
        val sqlStatements: List<String> = sql.substringAfter('\'').let {
            if (!it.startsWith(KEY_IDENTIFIER)) return super.execute(sql) else it
        }.substring(KEY_IDENTIFIER.length)
            .substringBeforeLast('\'')
            .split('.')
            .mapNotNull { encodedStatement ->
                if (encodedStatement.isBlank()) return@mapNotNull null

                val sb = StringBuilder()

                try {
                    Base64.Default.newDecoderFeed { b ->
                        sb.append(b.toInt().toChar())
                    }.use { feed ->
                        encodedStatement.forEach { c -> feed.consume(c) }
                    }
                } catch (e: EncodingException) {
                    throw SQLException("Failed to apply cipher parameters", e)
                }

                val sbLen = sb.length

                val statement = sb.toString()
                sb.clear()
                repeat(sbLen) { sb.append(' ') }

                statement.ifBlank { null }
            }

        for (statement in sqlStatements) {
            super.execute(statement)
        }

        return true
    }
}
