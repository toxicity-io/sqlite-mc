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
package io.toxicity.sqlite.mc.driver.config

import io.toxicity.sqlite.mc.driver.EphemeralOpt
import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import io.toxicity.sqlite.mc.driver.internal.ext.appendIndent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * An optional config which declares PRAGMA statements that are
 * to be executed whenever a connection is opened.
 *
 * @see [Builder]
 * */
public class PragmaConfig private constructor(
    public val ephemeral: Map<String, String>,
    public val filesystem: Map<String, String>,
) {

    public fun new(
        block: Builder.() -> Unit,
    ): PragmaConfig = Builder(other = this).apply(block).build()

    public companion object {

        /**
         * An empty config with no additional PRAGMA statement
         * configuration for new connections.
         * */
        @JvmField
        public val Default: PragmaConfig = new(null) {}

        /**
         * Helper for creating a new configuration to share
         * across different [SQLiteMCDriver.Factory]s.
         * */
        @JvmStatic
        public fun new(
            other: PragmaConfig?,
            block: Builder.() -> Unit,
        ): PragmaConfig = Builder(other).apply(block).build()
    }

    /**
     * Configure PRAGMA statements.
     *
     * e.g.
     *
     *     pragmas {
     *         // For both ephemeral and filesystem connections
     *         put("busy_timeout", 3_000.toString())
     *
     *         // ephemeral connections only
     *         ephemeral.put("secure_delete", "false")
     *
     *         // filesystem connections only
     *         filesystem.put("secure_delete", "fast")
     *     }
     *
     * See >> [PRAGMA statements](https://www.sqlite.org/pragma.html)
     * */
    @MCConfigDsl
    public class Builder internal constructor(other: PragmaConfig?) {

        /**
         * PRAGMA statements for database connections opened using an
         * [EphemeralOpt]
         * */
        @JvmField
        public val ephemeral: MutableMap<String, String> = mutableMapOf()

        /**
         * PRAGMA statements for database connections opened using a
         * [Key] which exist on the filesystem.
         * */
        @JvmField
        public val filesystem: MutableMap<String, String> = mutableMapOf()

        /**
         * Updates both [ephemeral] and [filesystem] maps
         * */
        @MCConfigDsl
        public fun put(key: String, value: String): Builder {
            ephemeral[key] = value
            filesystem[key] = value
            return this
        }

        /**
         * Updates both [ephemeral] and [filesystem] maps
         * */
        @MCConfigDsl
        public fun put(pair: Pair<String, String>): Builder {
            ephemeral[pair.first] = pair.second
            filesystem[pair.first] = pair.second
            return this
        }

        /**
         * Updates both [ephemeral] and [filesystem] maps
         * */
        @MCConfigDsl
        public fun putAll(map: Map<String, String>): Builder {
            ephemeral.putAll(map)
            filesystem.putAll(map)
            return this
        }

        /**
         * Updates both [ephemeral] and [filesystem] maps
         * */
        @MCConfigDsl
        public fun putAll(pairs: Iterable<Pair<String, String>>): Builder {
            ephemeral.putAll(pairs)
            filesystem.putAll(pairs)
            return this
        }

        init {
            if (other != null) {
                ephemeral.putAll(other.ephemeral)
                filesystem.putAll(other.filesystem)
            }
        }

        @JvmSynthetic
        internal fun build(): PragmaConfig {

            Pragma.MC.ALL.forEach { pragma ->
                ephemeral.remove(pragma.name)
                filesystem.remove(pragma.name)
            }

            BLACK_LISTED.forEach { pragma ->
                ephemeral.remove(pragma)
                filesystem.remove(pragma)
            }

            return PragmaConfig(
                ephemeral = ephemeral.toMap(),
                filesystem = filesystem.toMap(),
            )
        }

        private companion object {
            private val BLACK_LISTED by lazy {
                buildSet {
                    // Handled automatically by the driver upon instantiation
                    add("user_version")
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return  other is PragmaConfig
                && other.ephemeral.entries == ephemeral.entries
                && other.filesystem.entries == filesystem.entries
    }

    override fun hashCode(): Int {
        var result = 17
        ephemeral.entries.forEach { entry ->
            result = result * 31 + entry.key.hashCode()
            result = result * 31 + entry.value.hashCode()
        }
        filesystem.entries.forEach { entry ->
            result = result * 31 + entry.key.hashCode()
            result = result * 31 + entry.value.hashCode()
        }
        return result
    }

    override fun toString(): String {
        return buildString {
            append(this@PragmaConfig::class.simpleName)
            appendLine(": [")

            appendIndent("ephemeral: [")
            if (ephemeral.isEmpty()) {
                appendLine(']')
            } else {
                appendLine()
                ephemeral.entries.forEach { entry ->
                    appendIndent(entry, indent = 2)
                    appendLine()
                }
                appendIndent(']')
                appendLine()
            }

            appendIndent("filesystem: [")
            if (filesystem.isEmpty()) {
                appendLine(']')
            } else {
                appendLine()
                filesystem.entries.forEach { entry ->
                    appendIndent(entry, indent = 2)
                    appendLine()
                }
                appendIndent(']')
                appendLine()
            }

            append(']')
        }
    }
}
