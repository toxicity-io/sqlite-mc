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

import org.sqlite.JDBC
import java.sql.Connection
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.SQLException
import java.util.*

internal class JDBCMC private constructor(): JDBC() {

    override fun acceptsURL(url: String?): Boolean = isValidURL(url)

    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> {
        val jdbcInfo = super.getPropertyInfo(url, info)
        // TODO: Add Pragma.MC info
        return jdbcInfo
    }

    @Throws(SQLException::class)
    override fun connect(url: String?, info: Properties?): Connection? = JDBCMC4Connection.of(url, info)

    internal companion object {

        internal const val PREFIX: String = "jdbc:sqlite-mc:"

        @JvmStatic
        internal fun isValidURL(url: String?): Boolean = url?.lowercase()?.startsWith(PREFIX) ?: false

        init {
            try {
                DriverManager.registerDriver(JDBCMC())
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }
}
