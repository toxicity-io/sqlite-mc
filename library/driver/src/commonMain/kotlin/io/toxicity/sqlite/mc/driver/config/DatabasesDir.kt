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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "ConvertSecondaryConstructorToPrimary")

package io.toxicity.sqlite.mc.driver.config

import kotlin.jvm.JvmSynthetic

/**
 * The directory in which databases reside.
 * */
public expect class DatabasesDir {

    internal val path: String

    public fun pathOrNull(): String?

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
internal inline fun DatabasesDir.commonEquals(other: Any?): Boolean {
    return other is DatabasesDir && other.pathOrNull() == pathOrNull()
}
@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
internal inline fun DatabasesDir.commonHashCode(): Int = 17 * 31 + pathOrNull().hashCode()
@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
internal inline fun DatabasesDir.commonToString(): String = pathOrNull() ?: ""
