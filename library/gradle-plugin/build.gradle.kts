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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("publication")
    id("java-gradle-plugin")
    id(libs.plugins.build.config.get().pluginId)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

gradlePlugin {
    plugins {
        create("sqliteMC") {
            id = "io.toxicity.sqlite-mc"
            implementationClass = "io.toxicity.sqlite.mc.gradle.SQLiteMCPlugin"
        }
    }
}

dependencies {
    api(libs.gradle.sql.delight)
    implementation(libs.gradle.kotlin)
    testImplementation(kotlin("test"))
}

buildConfig {
    className("Versions")
    packageName("io.toxicity.sqlite.mc.gradle")

    useKotlinOutput {
        topLevelConstants = true
        internalVisibility = true
    }

    buildConfigField("String", "VERSION", "\"$version\"")
    buildConfigField("String", "SQLITE_DIALECT", "\"${libs.versions.sql.delight.dialect.get()}\"")
}

tasks.named<Test>("test") {
    dependsOn(
        ":library:driver:publishAllPublicationsToInstallLocallyRepository",
        ":library:android-unit-test:publishAllPublicationsToInstallLocallyRepository",
        ":library:gradle-plugin:publishAllPublicationsToInstallLocallyRepository"
    )

    useJUnit()
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
    environment("ORG_GRADLE_PROJECT_sqlitemcVersion", project.version)
}

tasks.named<ValidatePlugins>("validatePlugins") {
    enableStricterValidation.set(true)
}
