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
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.IOException
import java.io.InputStream
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

            targets.filterIsInstance<KotlinNativeTarget>().forEach { it.sqlite3mcInterop() }
        }
    }
}

fun KotlinNativeTarget.sqlite3mcInterop() {
    val libsDir = rootDir
        .resolve("external")
        .resolve("native")
        .resolve("libs")
        .resolve(konanTarget.name.substringBefore('_'))
        .resolve(konanTarget.name.substringAfter('_'))

    val staticLib = libsDir.resolve(
        konanTarget.family.staticPrefix
        + "sqlite3mc."
        + konanTarget.family.staticSuffix
    )

    // TODO: Remove once all build targets have static lib compilations
    if (!staticLib.exists()) return

    compilations["main"].apply {
        kotlinOptions {
            freeCompilerArgs += listOf("-include-binary", staticLib.path)
            freeCompilerArgs += listOf("-linker-options", "-L$libsDir -lsqlite3mc")
        }

        cinterops.create("sqlite3mc").apply {
            includeDirs(libsDir.resolve("include").path)
        }
    }
}

/**
 * Repack dependency jar files.
 *
 * For SqlDelight's sqlite-driver (JVM), it will extract only
 * that jar file to the build/jdbc-repack directory. This is
 * because the sqlite-driver depends on xerial/sqlite-jdbc which
 * we do not want as a transitive dependency since we are compiling
 * our own sqlite-jdbc jar that uses SQLite3MultipleCiphers.
 *
 * For JVM, Mac/Windows/Linux/Linux-Musl/FreeBSD binaries are
 * repackaged, while Linux-Android is dropped.
 *
 * For Android, all native binaries are dropped and Linux-Android
 * binaries are extracted to their respective src/androidMain/jniLibs
 * directories for this module. The remaining binaries are extracted
 * to the android-unit-test module which enables support for running
 * android unit tests (not instrumented, just unit tests).
 *
 * Will automatically run if a dependency's version updates.
 * Alternatively, can force it to repackage by deleting the
 * build/jdbc-repack directory (i.e. ./gradlew clean) and re-syncing
 * gradle.
 * */
private class JdbcRepack {

    val dirJniLibs = projectDir
        .resolve("src")
        .resolve("androidMain")
        .resolve("jniLibs")

    val jarSQLDelightDriver: File
    val jarSQLiteJDBCAndroid: File
    val jarSQLiteJDBCJvm: File

    val depSQLDelightDriver: MinimalExternalModuleDependency = libs.sql.delight.driver.jvm.get()
    val depSQLiteJDBC: MinimalExternalModuleDependency = libs.sql.jdbc.xerial.get()

    private fun MinimalExternalModuleDependency.toJarFileName(): String = "$name-$version.jar"

    init {
        val repackDir = buildDir
            .resolve("jdbc-repack")
        jarSQLDelightDriver = repackDir
            .resolve(depSQLDelightDriver.toJarFileName())
        jarSQLiteJDBCAndroid = repackDir
            .resolve("android")
            .resolve(depSQLiteJDBC.toJarFileName())
        jarSQLiteJDBCJvm = repackDir
            .resolve("jvm")
            .resolve(depSQLiteJDBC.toJarFileName())

        if (
            !jarSQLDelightDriver.exists()
            || !jarSQLiteJDBCAndroid.exists()
            || !jarSQLiteJDBCJvm.exists()
        ) {
            Repackage()
        }
    }

    private inner class Repackage {

        private val configJDBCRepack: Configuration = configurations.create("jdbc-repack")

        init {
            dependencies {
                "jdbc-repack"(depSQLDelightDriver)
            }
        }

        // Only want the jar file and not xerial/sqlite-jdbc dependency
        // b/c we're using a custom build which uses SQLite3MultipleCiphers
        private val repackSQLDelightDriver by lazy {
            val jarFile = configJDBCRepack.files.first { file ->
                file.absolutePath.contains(depSQLDelightDriver.group.toString())
                && file.name == depSQLDelightDriver.toJarFileName()
            }

            copy {
                from(jarFile)
                into(jarSQLDelightDriver.parentFile)
            }
        }

        private val libsSQLiteJDBCJar: File = rootDir
            .resolve("external")
            .resolve("jni")
            .resolve("libs")
            .resolve(depSQLiteJDBC.toJarFileName())

        private val repackSQLiteJDBCAndroid by lazy {
            if (jarSQLiteJDBCAndroid.exists()) return@lazy
            jarSQLiteJDBCAndroid.ensureParentDirsCreated()

            val libsJar = JarFile(libsSQLiteJDBCJar)

            JarOutputStream(jarSQLiteJDBCAndroid.outputStream()).use { oStream ->

                val jniLibName = "libsqlitejdbc.so"

                libsJar.entries().iterator().forEach { entry ->

                    // Exclude all binary resources from the repackaged
                    // .jar file, while extracting only the android libs
                    // to their respective jniLibs directory for this module
                    if (entry.name.startsWith("org/sqlite/native")) {
                        when {
                            entry.name.endsWith("/Linux-Android/aarch64/$jniLibName") -> {
                                libsJar.extractEntryTo(
                                    entry,
                                    dirJniLibs.resolve("arm64-v8a").resolve(jniLibName),
                                )
                            }
                            entry.name.endsWith("/Linux-Android/arm/$jniLibName") -> {
                                libsJar.extractEntryTo(
                                    entry,
                                    dirJniLibs.resolve("armeabi-v7a").resolve(jniLibName),
                                )
                            }
                            entry.name.endsWith("/Linux-Android/x86/$jniLibName") -> {
                                libsJar.extractEntryTo(
                                    entry,
                                    dirJniLibs.resolve("x86").resolve(jniLibName),
                                )
                            }
                            entry.name.endsWith("/Linux-Android/x86_64/$jniLibName") -> {
                                libsJar.extractEntryTo(
                                    entry,
                                    dirJniLibs.resolve("x86_64").resolve(jniLibName),
                                )
                            }
                            else -> { /* ignore */ }
                        }

                        return@forEach
                    }

                    libsJar.extractEntryTo(entry, oStream)
                }
            }
        }

        private val repackSQLiteJDBCJvm by lazy {
            if (jarSQLiteJDBCJvm.exists()) return@lazy
            jarSQLiteJDBCJvm.ensureParentDirsCreated()

            val libsJar = JarFile(libsSQLiteJDBCJar)

            val signedLibsDir: File = rootDir
                .resolve("external")
                .resolve("jni")
                .resolve("libs")
                .resolve("signed")

            val androidUnitTestResDir = projectDir
                .resolveSibling("android-unit-test")
                .resolve("src")
                .resolve("main")
                .resolve("resources")

            androidUnitTestResDir.deleteRecursively()

            JarOutputStream(jarSQLiteJDBCJvm.outputStream()).use { oStream ->
                libsJar.entries().iterator().forEach { entry ->

                    // Don't include the readme or any android libs in
                    // the repackaged .jar file
                    if (
                        entry.name == "org/sqlite/native/readme.txt"
                        || entry.name.startsWith("org/sqlite/native/Linux-Android")
                    ) {
                        return@forEach
                    }

                    val signedLib: File? = when {
                        entry.isDirectory -> null
                        entry.name.startsWith("org/sqlite/native/Mac/aarch64") -> {
                            signedLibsDir
                                .resolve("Mac")
                                .resolve("aarch64")
                                .resolve("libsqlitejdbc.dylib")
                        }
                        entry.name.startsWith("org/sqlite/native/Mac/x86_64") -> {
                            signedLibsDir
                                .resolve("Mac")
                                .resolve("x86_64")
                                .resolve("libsqlitejdbc.dylib")
                        }
                        entry.name.startsWith("org/sqlite/native/Windows/x86_64") -> {
                            signedLibsDir
                                .resolve("Windows")
                                .resolve("x86_64")
                                .resolve("sqlitejdbc.dll")
                        }
                        // Must come after check for Windows/x86_64
                        entry.name.startsWith("org/sqlite/native/Windows/x86") -> {
                            signedLibsDir
                                .resolve("Windows")
                                .resolve("x86")
                                .resolve("sqlitejdbc.dll")
                        }

                        // All other native libs within the jni/libs .jar
                        // file (that are not codesigned), extract them
                        // to android-unit-test module's resource directory
                        entry.name.startsWith("org/sqlite/native") -> {
                            libsJar.extractEntryTo(entry, androidUnitTestResDir.resolve(entry.name))
                            null
                        }
                        else -> null
                    }

                    if (signedLib != null) {
                        // Instead of repackaging the unsigned lib
                        // that the external/jni/libs/sqlite-jdbc-{version}.jar
                        // file contains, hot swap the codesigned lib into the
                        // repackaged .jar file
                        oStream.putNextEntry(entry)
                        signedLib.inputStream().use { iStream ->
                            iStream.extractTo(oStream)
                        }

                        // Also make sure to write signed lib to android-unit-test
                        // module's resource directory
                        val resFile = androidUnitTestResDir.resolve(entry.name)
                        resFile.prepareFileWrite()

                        signedLib.inputStream().use { iStream ->
                            resFile.outputStream().use { oResStream ->
                                iStream.extractTo(oResStream)
                            }
                        }
                    } else {
                        libsJar.extractEntryTo(entry, oStream)
                    }
                }
            }
        }

        init {
            tasks.withType<KotlinJvmCompile> {
                if (name == "compileKotlinJvm") {
                    repackSQLDelightDriver
                    repackSQLiteJDBCJvm
                }
                if (name == "compileDebugKotlinAndroid" || name == "compileReleaseKotlinAndroid") {
                    repackSQLDelightDriver
                    repackSQLiteJDBCAndroid
                }
            }
        }

        @Throws(IOException::class)
        private fun JarFile.extractEntryTo(entry: JarEntry, destination: File) {
            destination.prepareFileWrite()

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
                throw IOException("JarEntry is a directory. Cannot write to OutputStream >> $entry")
            }

            getInputStream(entry).use { iStream ->
                iStream.extractTo(oStream)
            }
        }

        @Throws(IOException::class)
        private fun InputStream.extractTo(oStream: OutputStream) {
            val buf = ByteArray(4096)

            try {
                while (true) {
                    val read = read(buf)
                    if (read == -1) break
                    oStream.write(buf, 0, read)
                }
            } finally {
                if (oStream is JarOutputStream) {
                    oStream.closeEntry()
                }
            }
        }

        @Throws(IOException::class)
        private fun File.prepareFileWrite() {
            if (isDirectory) {
                throw IOException("destination must be a file, but isDirectory[true] >> $this")
            }
            if (exists() && !delete()) {
                throw IOException("Failed to delete destination file >> $this")
            }

            ensureParentDirsCreated()
        }
    }
}
