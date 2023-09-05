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

import io.toxicity.sqlite.mc.driver.config.encryption.MCRC4Config
import io.toxicity.sqlite.mc.driver.config.encryption.MCSqlCipherConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EncryptionMigrationConfigUnitTest {

    private val databasesDir = DatabasesDir(File("/path/to/databases"))
    private fun filesystem(
        block: FilesystemConfig.Builder.() -> Unit
    ) = FilesystemConfig.Builder(databasesDir).apply(block).build()

    @Test
    fun givenConfig_whenExpressedTwice_thenReplacesFirst() {
        val filesystem = filesystem {
            encryptionMigrations {
                migrationFrom {
                    rc4 { default() }

                    chaCha20 { sqleet() }
                }
                migrationFrom {
                    chaCha20 { default() }
                }
            }

            // Overrides the first one
            encryptionMigrations {
                migrationFrom {
                    rc4 { default() }
                }
                migrationFrom {
                    sqlCipher { v4() }
                }
            }

            encryption {
                sqlCipher {
                    default {
                        kdfIter(500_000)
                    }
                }
            }
        }

        assertNotNull(filesystem.encryptionMigrationConfig)
        assertEquals(2, filesystem.encryptionMigrationConfig?.migrations?.size)
        assertEquals(MCRC4Config.Default, filesystem.encryptionMigrationConfig!!.migrations.elementAt(0).encryptionConfig.cipherConfig)
        assertEquals(MCSqlCipherConfig.v4, filesystem.encryptionMigrationConfig!!.migrations.elementAt(1).encryptionConfig.cipherConfig)
    }
}
