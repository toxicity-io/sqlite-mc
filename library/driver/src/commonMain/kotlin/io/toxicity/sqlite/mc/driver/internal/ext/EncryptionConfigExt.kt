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
package io.toxicity.sqlite.mc.driver.internal.ext

import io.toxicity.sqlite.mc.driver.config.MutablePragmas
import io.toxicity.sqlite.mc.driver.config.Pragma
import io.toxicity.sqlite.mc.driver.config.encryption.EncryptionConfig
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import io.toxicity.sqlite.mc.driver.config.mutablePragmas
import kotlin.jvm.JvmSynthetic

@JvmSynthetic
@Throws(IllegalArgumentException::class, IllegalStateException::class)
internal fun EncryptionConfig.createRekeyParameters(
    key: Key,
    isInMemory: Boolean
): Pair<MutablePragmas, List<String>> {
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

    return Pair(pragmas, sqlStatements)
}
