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

import io.toxicity.sqlite.mc.driver.MCConfigDsl
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Platform specific configuration options for Native or Jvm
 * */
public expect class PlatformConfig {

    public fun new(
        block: Builder.() -> Unit,
    ): PlatformConfig

    public companion object {

        @JvmField
        public val Default: PlatformConfig

        @JvmStatic
        public fun new(
            other: PlatformConfig?,
            block: Builder.() -> Unit,
        ): PlatformConfig
    }

    @MCConfigDsl
    public class Builder internal constructor(other: PlatformConfig?) {

        @JvmSynthetic
        internal fun build(): PlatformConfig
    }

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
