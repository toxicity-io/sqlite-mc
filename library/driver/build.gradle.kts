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
import co.touchlab.cklib.gradle.CompileToBitcode.Language.C
import co.touchlab.cklib.gradle.CompileToBitcodeExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.konan.target.Architecture.*
import org.jetbrains.kotlin.konan.target.Family.*
import org.jetbrains.kotlin.konan.target.KonanTarget
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

                lint {
                    // androidx.startup.InitializationProvider "missing", but really not.
                    disable.add("MissingClass")
                }
            }

            sourceSetMain {
                dependencies {
                    implementation(libs.androidx.startup.runtime)
                    implementation(files(jdbcRepack.jarSQLiteJDBCAndroid))
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
            pluginIds("publication", libs.plugins.cklib.get().pluginId)

            sourceSetMain {
                dependencies {
                    api(libs.sql.delight.runtime)
                    implementation(libs.encoding.base16)
                    implementation(libs.immutable.collections)
                    implementation(libs.kotlinx.coroutines.core)
                }
            }
        }

        kotlin {
            sourceSets.findByName("jvmAndroidMain")?.apply {
                dependencies {
                    api(libs.sql.delight.driver.jdbc)

                    compileOnly(jdbcRepack.depSQLiteJDBC)
                    compileOnly(jdbcRepack.depSQLDelightDriver)

                    implementation(libs.encoding.base64)
                    implementation(files(jdbcRepack.jarSQLDelightDriver))
                }
            }
        }

        kotlin {
            sourceSets.findByName("nativeMain")?.apply {
                dependencies {
                    implementation(libs.sql.delight.driver.native)
                }
            }
        }

        kotlin {
            project.tasks.withType<Jar> {
                if (name != "jvmJar") return@withType
                from(zipTree(jdbcRepack.jarSQLiteJDBCJvm))
                from(zipTree(jdbcRepack.jarSQLDelightDriver))
            }
        }

        kotlin {
            val cinteropTaskInfo = targets.filterIsInstance<KotlinNativeTarget>().map { target ->
                target.compilations["test"].cinterops.create("cklib-dl") {
                    definitionFile.set(projectDir.resolve("$name.def"))
                }.interopProcessingTaskName to target.konanTarget
            }

            project.extensions.configure<CompileToBitcodeExtension>("cklib") {
                config.kotlinVersion = libs.versions.gradle.kotlin.get()

                create("sqlite3mc") {
                    language = C
                    srcDirs = project.files(file("sqlite3mc"))

                    val kt = KonanTarget.predefinedTargets[target]!!

                    // Add as dependency so any kotlin native tooling is downloaded
                    // before the CompileToBitcode task is executed.
                    cinteropTaskInfo.forEach { (taskName, target) ->
                        if (target != kt) return@forEach
                        this.dependsOn(taskName)
                    }

                    // -O3 set automatically for C language

                    listOf(
                        "-fvisibility=hidden",
                    ).let { compilerArgs.addAll(it) }

                    // Architecture specific flags
                    when (kt.architecture) {
                        X64, X86 -> listOf(
                            "-msse4.2",
                            "-maes",
                        )
                        else -> null
                    }?.let { compilerArgs.addAll(it) }

                    // Warning/Error suppression flags
                    buildList {
                        add("-Wno-sign-compare")
                        add("-Wno-unused-function")
                        add("-Wno-unused-parameter")
                        add("-Wno-unused-variable")

                        if (kt.family.isAppleFamily) {
                            add("-Wno-missing-braces")
                            add("-Wno-missing-field-initializers")
                            add("-Wno-unused-command-line-argument")
                            // disable warning about gethostuuid being deprecated on darwin
                            add("-Wno-#warnings")
                        }
                    }.let { compilerArgs.addAll(it) }

                    // SQLITE flags
                    when (kt.family) {
                        IOS, TVOS, WATCHOS -> listOf(
                            // gethostuuid is deprecated
                            //
                            // D.Richard Hipp (SQLite architect) suggests for non-macOS:
                            // "The SQLITE_ENABLE_LOCKING_STYLE thing is an apple-only
                            // extension that boosts performance when SQLite is used
                            // on a network filesystem. This is important on macOS because
                            // some users think it is a good idea to put their home
                            // directory on a network filesystem.
                            //
                            // I'm guessing this is not really a factor on iOS."
                            "-DSQLITE_ENABLE_LOCKING_STYLE=0",
                        )
                        else -> null
                    }?.let { compilerArgs.addAll(it) }

                    if (kt.family.isAppleFamily) {
                        // Options that SQLite is compiled with on
                        // Darwin devices. macOS 10.11.6+, iOS 9.3.5+
                        listOf(
                            "-DSQLITE_ENABLE_API_ARMOR",
                            "-DSQLITE_OMIT_AUTORESET",
                        ).let { compilerArgs.addAll(it) }
                    }

                    listOf(
                        // 2 (Multi-Threaded) is the default for Darwin
                        // targets, but on JVM it is using 1 (Serialized).
                        //
                        // SQLDelight's NativeSqliteDriver utilizes thread pools
                        // and nerfs any benefit that Serialized would offer, so.
                        //
                        // This *might* change in the future if migrating away from
                        // SQLDelight's NativeSqliteDriver and SQLiter
                        "-DSQLITE_THREADSAFE=2",

                        // This removes extension loading entirely. On Jvm, this flag
                        // is needed at compile time because of the JNI interface, but
                        // for native it is completely disabled.
                        "-DSQLITE_OMIT_LOAD_EXTENSION",

                        // Remaining flags are what JVM is compiled with
                        "-DSQLITE_HAVE_ISNAN=1",
                        "-DHAVE_USLEEP=1",
                        "-DSQLITE_ENABLE_COLUMN_METADATA=1",
                        "-DSQLITE_CORE=1",
                        "-DSQLITE_ENABLE_FTS3=1",
                        "-DSQLITE_ENABLE_FTS3_PARENTHESIS=1",
                        "-DSQLITE_ENABLE_FTS5=1",
                        "-DSQLITE_ENABLE_RTREE=1",
                        "-DSQLITE_ENABLE_STAT4=1",
                        "-DSQLITE_ENABLE_DBSTAT_VTAB=1",
                        "-DSQLITE_ENABLE_MATH_FUNCTIONS=1",
                        "-DSQLITE_DEFAULT_MEMSTATUS=0",
                        "-DSQLITE_DEFAULT_FILE_PERMISSIONS=0666",
                        "-DSQLITE_MAX_VARIABLE_NUMBER=250000",
                        "-DSQLITE_MAX_MMAP_SIZE=0",
                        "-DSQLITE_MAX_LENGTH=2147483647",
                        "-DSQLITE_MAX_COLUMN=32767",
                        "-DSQLITE_MAX_SQL_LENGTH=1073741824",
                        "-DSQLITE_MAX_FUNCTION_ARG=127",
                        "-DSQLITE_MAX_ATTACHED=125",
                        "-DSQLITE_MAX_PAGE_COUNT=4294967294",
                        "-DSQLITE_DISABLE_PAGECACHE_OVERFLOW_STATS",
                        "-DSQLITE_DQS=0",
                        "-DCODEC_TYPE=CODEC_TYPE_CHACHA20",
                        "-DSQLITE_ENABLE_EXTFUNC=1",
                        "-DSQLITE_ENABLE_REGEXP=1",
                        "-DSQLITE_TEMP_STORE=2",
                        "-DSQLITE_USE_URI=1",
                        "-DWXSQLITE3_HAVE_CIPHER_AEGIS=0",
                    ).let { compilerArgs.addAll(it) }

                    // Linker (llvm-link) options
                    listOf(
                        "--only-needed",
                    ).let { linkerArgs.addAll(it) }
                }
            }
        }
    }
}

tasks.getByName("clean") {
    doLast {
        projectDir
            .resolve("src")
            .resolve("androidMain")
            .resolve("jniLibs")
            .deleteRecursively()
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
 * For JVM, Mac/Windows/Linux/Linux-Android/Linux-Musl/FreeBSD binaries are
 * repackaged.
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
        val repackDir = layout.buildDirectory.asFile.get()
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
            @Suppress("RemoveRedundantCallsOfConversionMethods")
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
                    if (entry.name == "org/sqlite/native/readme.txt") return@forEach

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

                        // All other native libs within the external/libs .jar
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
                        // that the external/libs/sqlite-jdbc-{version}.jar
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
