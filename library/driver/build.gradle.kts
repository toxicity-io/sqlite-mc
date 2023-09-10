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
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.IOException
import java.io.OutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

plugins {
    id("configuration")
}

private val jdbcRepack = JdbcRepack()

kmpConfiguration {
    configureShared {

        androidLibrary(namespace = "io.toxicity.sqlite.mc.driver") {
            target { publishLibraryVariants("release") }

            android {
                sourceSets["main"].jniLibs.srcDir(jdbcRepack.dirJniLibs.path)

                defaultConfig {
                    ndk {
                        abiFilters.add("arm64-v8a")
                        abiFilters.add("armeabi-v7a")
                        abiFilters.add("x86")
                        abiFilters.add("x86_64")
                    }
                }
            }

            sourceSetMain {
                dependencies {
                    implementation(files(jdbcRepack.jarSQLiteJDBCAndroid))
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(project(":library:android-unit-test"))
                }
            }
            sourceSetTestInstrumented {
                dependencies {
                    implementation(libs.androidx.test.core)
                    implementation(libs.androidx.test.runner)
                }
            }
        }

        jvm {
            sourceSetMain {
                dependencies {
                    implementation(files(jdbcRepack.jarSQLiteJDBCJvm))
                }
            }
        }

        common {
            pluginIds("publication")

            sourceSetMain {
                dependencies {
                    api(libs.sql.delight.runtime)
                    implementation(libs.encoding.base16)
                    implementation(libs.kotlinx.coroutines.core)
                }
            }
        }

        kotlin {
            sourceSets {
                findByName("jvmAndroidMain")?.apply {
                    dependencies {
                        implementation(libs.encoding.base64)

                        api(libs.sql.delight.driver.jdbc)
                        compileOnly(jdbcRepack.depSQLiteJDBC)

                        compileOnly(jdbcRepack.depSQLDelightDriver)
                        implementation(files(jdbcRepack.jarSQLDelightDriver))
                    }
                }

                findByName("nativeMain")?.apply {
                    dependencies {
                        implementation(libs.sql.delight.driver.native)
                    }
                }
            }
        }
    }
}

/**
 * Repack dependency jar files.
 *
 * For SqlDelight's sqlite-driver (JVM), it will extract only
 * that jar file to the /library/jdbc-repack directory. This is
 * because the sqlite-driver depends on xerial/sqlite-jdbc which
 * we do not want as a transitive dependency. This allows swapping it
 * out for Willena/sqlite-jdbc-crypt (a fork of xerial/sqlite-jdbc),
 *
 * For JVM, Mac/Windows/Linux/Linux-Musl/FreeBSD binaries are
 * repackaged, while Linux-Android is dropped.
 *
 * For Android, all native binaries are dropped and Linux-Android
 * binaries are extracted to their respective src/androidMain/jniLibs
 * directories of this project. The remaining binaries are extracted
 * to the android-unit-test module which enables support for running
 * android unit tests (not instrumented, just unit tests).
 *
 * Will automatically run if a dependency's version updates. Alternatively,
 * can force it to repackage by deleting the /library/jdbc-repack directory
 * and performing a gradle sync.
 * */
private class JdbcRepack(
    // For debug purposes, can set to false
    // and will use xerial/sqlite-jdbc
    useSQLiteJDBCCrypt: Boolean = true
) {

    val dirJniLibs = projectDir
        .resolve("src")
        .resolve("androidMain")
        .resolve("jniLibs")

    val jarSQLDelightDriver: File
    val jarSQLiteJDBCAndroid: File
    val jarSQLiteJDBCJvm: File

    val depSQLDelightDriver: MinimalExternalModuleDependency = libs.sql.delight.driver.jvm.get()
    val depSQLiteJDBC: MinimalExternalModuleDependency = if (useSQLiteJDBCCrypt) {
        libs.sql.jdbc.crypt.get()
    } else {
        libs.sql.jdbc.xerial.get()
    }

    init {
        val repackDir = projectDir.resolveSibling("jdbc-repack")

        jarSQLDelightDriver = repackDir.resolve(depSQLDelightDriver.toJarFileName())
        jarSQLiteJDBCAndroid = repackDir.resolve("android").resolve(depSQLiteJDBC.toJarFileName())
        jarSQLiteJDBCJvm = repackDir.resolve("jvm").resolve(depSQLiteJDBC.toJarFileName())

        if (
            !jarSQLDelightDriver.exists()
            || !jarSQLiteJDBCAndroid.exists()
            || !jarSQLiteJDBCJvm.exists()
        ) {
            Repackage()
        }
    }

    private fun MinimalExternalModuleDependency.toJarFileName(): String = "$name-$version.jar"

    private inner class Repackage {

        private val configJdbcRepack: Configuration = configurations.create("jdbc-repack")

        init {
            dependencies {
                "jdbc-repack"(depSQLiteJDBC)
                "jdbc-repack"(depSQLDelightDriver)
            }
        }

        // Only want the jar file and not xerial/sqlite-jdbc dependency
        // b/c we're using a fork (Willena/sqlite-jdbc-crypt)
        private val repackSqliteDriver by lazy {
            val jarFile = configJdbcRepack.files.first { file ->
                file.absolutePath.contains(depSQLDelightDriver.group.toString())
                && file.name == depSQLDelightDriver.toJarFileName()
            }

            copy {
                from(jarFile)
                into(jarSQLDelightDriver.parentFile)
            }
        }

        private val jdbcSqliteJar: File by lazy {
            configJdbcRepack.files.first { file ->
                file.absolutePath.contains(depSQLiteJDBC.group.toString())
                && file.name == depSQLiteJDBC.toJarFileName()
            }
        }

        private val repackJdbcSqliteAndroid by lazy {
            if (jarSQLiteJDBCAndroid.exists()) return@lazy
            jarSQLiteJDBCAndroid.ensureParentDirsCreated()

            val jf = JarFile(jdbcSqliteJar)

            val mcDriverAndroidTestResources = projectDir
                .resolveSibling("android-unit-test")
                .resolve("src")
                .resolve("main")
                .resolve("resources")

            mcDriverAndroidTestResources.deleteRecursively()

            JarOutputStream(jarSQLiteJDBCAndroid.outputStream()).use { oStream ->

                val soFileName = "libsqlitejdbc.so"

                jf.entries().iterator().forEach { entry ->

                    // Exclude all binary resources while extracting
                    // only the android .so files to their respective
                    // jniLibs directory.
                    if (entry.name.startsWith("org/sqlite/native")) {
                        when {
                            entry.name.endsWith("/Linux-Android/aarch64/$soFileName") -> {
                                jf.extractEntryTo(entry, dirJniLibs.resolve("arm64-v8a").resolve(soFileName))
                            }

                            entry.name.endsWith("/Linux-Android/arm/$soFileName") -> {
                                jf.extractEntryTo(entry, dirJniLibs.resolve("armeabi-v7a").resolve(soFileName))
                            }

                            entry.name.endsWith("/Linux-Android/x86/$soFileName") -> {
                                jf.extractEntryTo(entry, dirJniLibs.resolve("x86").resolve(soFileName))
                            }

                            entry.name.endsWith("/Linux-Android/x86_64/$soFileName") -> {
                                jf.extractEntryTo(entry, dirJniLibs.resolve("x86_64").resolve(soFileName))
                            }

                            entry.name.endsWith("readme.txt") -> {}
                            else -> {
                                if (!entry.isDirectory) {
                                    // So UnitTests have platform support
                                    jf.extractEntryTo(entry, mcDriverAndroidTestResources.resolve(entry.name))
                                }
                            }
                        }

                        return@forEach
                    }

                    jf.extractEntryTo(entry, oStream)
                }
            }
        }

        private val repackJdbcSqliteJvm by lazy {
            if (jarSQLiteJDBCJvm.exists()) return@lazy
            jarSQLiteJDBCJvm.ensureParentDirsCreated()

            val jf = JarFile(jdbcSqliteJar)

            JarOutputStream(jarSQLiteJDBCJvm.outputStream()).use { oStream ->
                jf.entries().iterator().forEach { entry ->

                    // Exclude resources for platforms that will not be run on.
                    if (
                        entry.name == "org/sqlite/native/readme.txt"
//                        || entry.name.startsWith("org/sqlite/native/FreeBSD")
//                        || entry.name.startsWith("org/sqlite/native/Linux-Musl")
                        || entry.name.startsWith("org/sqlite/native/Linux-Android")
                    ) {
                        return@forEach
                    }

                    jf.extractEntryTo(entry, oStream)
                }
            }
        }

        init {
            tasks.withType<KotlinJvmCompile> {
                if (name == "compileKotlinJvm") {
                    repackSqliteDriver
                    repackJdbcSqliteJvm
                }
                if (name == "compileDebugKotlinAndroid" || name == "compileReleaseKotlinAndroid") {
                    repackSqliteDriver
                    repackJdbcSqliteAndroid
                }
            }
        }

        @Throws(IOException::class)
        private fun JarFile.extractEntryTo(entry: JarEntry, destination: File) {
            if (destination.isDirectory) {
                throw IOException("destination must be a file, but isDirectory[true] >> $destination")
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("Failed to delete destination file >> $destination")
            }

            destination.ensureParentDirsCreated()

            destination.outputStream().use { oStream ->
                extractEntryTo(entry, oStream)
            }
        }

        @Throws(IOException::class)
        private fun JarFile.extractEntryTo(entry: JarEntry, oStream: OutputStream) {
            if (oStream is JarOutputStream) {
                oStream.putNextEntry(entry)

                if (entry.isDirectory) {
                    oStream.closeEntry()
                    return
                }
            } else if (entry.isDirectory) {
                throw IOException("JarEntry is a directory >> $entry")
            }

            getInputStream(entry).use { iStream ->
                val buf = ByteArray(4096)

                while (true) {
                    val read = iStream.read(buf)
                    if (read == -1) break
                    oStream.write(buf, 0, read)
                }
            }

            if (oStream is JarOutputStream) {
                oStream.closeEntry()
            }
        }
    }
}
