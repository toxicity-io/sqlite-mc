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
package io.toxicity.sqlite.mc.driver.config.encryption

import io.toxicity.sqlite.mc.driver.MCConfigDsl
import io.toxicity.sqlite.mc.driver.internal.ext.appendIndent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

public class EncryptionMigration(
    @JvmField
    public val note: String,
    @JvmField
    public val encryptionConfig: EncryptionConfig,
) {

    /**
     * Extends [EncryptionConfig.Builder]
     *
     * e.g.
     *
     *     encryptionMigrations {
     *         migrationFrom {
     *             note = """
     *                 some note
     *             """.trimIndent()
     *
     *             sqlCipher { v4() }
     *         }
     *     }
     * */
    @MCConfigDsl
    public class Builder internal constructor(): EncryptionConfig.Builder(null) {

        @JvmField
        public var note: String = ""

        @JvmSynthetic
        internal fun buildMigration(): EncryptionMigration? {
            val config = build() ?: return null
            return EncryptionMigration(note.trimIndent(), config)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is EncryptionMigration && other.encryptionConfig == encryptionConfig
    }

    override fun hashCode(): Int = 17 * 31 + encryptionConfig.hashCode()

    override fun toString(): String {
        return buildString {
            append(this@EncryptionMigration::class.simpleName)
            appendLine(": [")

            appendIndent("note: [")
            if (note.isNotBlank()) {
                appendLine()

                note.lines().forEach { line ->
                    appendIndent(line, indent = 2)
                    appendLine()
                }
                appendIndent(']')
                appendLine()
            } else {
                appendLine(']')
            }

            appendIndent("encryptionConfig: [")
            appendLine()

            val lines = encryptionConfig.toString().lines()

            for (i in 1..lines.lastIndex) {
                appendIndent(lines[i])
                appendLine()
            }
            append(']')
        }
    }
}
