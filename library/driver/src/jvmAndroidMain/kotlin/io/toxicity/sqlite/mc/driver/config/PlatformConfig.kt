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

package io.toxicity.sqlite.mc.driver.config

import app.cash.sqldelight.db.SqlSchema
import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.internal.ext.appendIndent

/**
 * Platform specific configuration options for Jvm
 * */
public actual class PlatformConfig private constructor(
    @JvmField
    public val migrateEmptySchema: Boolean,
) {

    public actual fun new(
        block: Builder.() -> Unit,
    ): PlatformConfig = new(other = this, block)

    public actual companion object {

        @JvmField
        public actual val Default: PlatformConfig = new(other = null) {}

        @JvmStatic
        public actual fun new(
            other: PlatformConfig?,
            block: Builder.() -> Unit,
        ): PlatformConfig = Builder(other).apply(block).build()
    }

    @MCConfigDsl
    public actual class Builder internal actual constructor(other: PlatformConfig?) {

        /**
         * If true, when the PRAGMA user_version returns a value of 0, instead
         * of invoking [SqlSchema.create], [SqlSchema.migrate] will be invoked
         * instead.
         * */
        @JvmField
        public var migrateEmptySchema: Boolean = false

        init {
            if (other != null) {
                migrateEmptySchema = other.migrateEmptySchema
            }
        }

        @JvmSynthetic
        internal actual fun build(): PlatformConfig = PlatformConfig(
            migrateEmptySchema = migrateEmptySchema
        )
    }

    actual override fun equals(other: Any?): Boolean {
        return  other is PlatformConfig
                && other.migrateEmptySchema == migrateEmptySchema
    }

    actual override fun hashCode(): Int {
        return 17 * 31 + migrateEmptySchema.hashCode()
    }

    actual override fun toString(): String {
        return buildString {
            append(this@PlatformConfig::class.simpleName)
            appendLine(": [")
            appendIndent("migrateEmptySchema: ")
            appendLine(migrateEmptySchema)
            append(']')
        }
    }
}
