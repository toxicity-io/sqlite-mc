# sqlite-mc
[![badge-license]][url-license]
[![badge-latest-release]][url-latest-release]

[![badge-kotlin]][url-kotlin]
[![badge-sqlite]][url-sqlite]
[![badge-coroutines]][url-coroutines]
[![badge-encoding]][url-encoding]
[![badge-immutable]][url-immutable]
[![badge-sqldelight]][url-sqldelight]
[![badge-sqlitemc]][url-sqlitemc]
[![badge-sqliter]][url-sqliter]
[![badge-sqlitejdbc]][url-sqlitejdbc]

![badge-platform-android]
![badge-platform-jvm]
![badge-platform-ios]
![badge-platform-tvos]
![badge-platform-watchos]
![badge-support-apple-silicon]
<!--
![badge-platform-js]
![badge-platform-js-node]
![badge-platform-linux]
![badge-platform-macos]
![badge-platform-wasm]
![badge-platform-windows]
![badge-support-android-native]
![badge-support-js-ir]
![badge-support-linux-arm]
-->

An [SQLDelight][url-sqldelight] driver that uses [SQLite3MultipleCiphers][url-sqlitemc] for 
database encryption.

### Usage

- Define `DatabasesDir`

  **Common (available for all targets):**
  ```kotlin
  // Use system default location for the given platform
  val databasesDir = DatabasesDir()
  val databasesDir = DatabasesDir(null) // null
  val databasesDir = DatabasesDir("     ") // blank

  // Specify a path string
  val databasesDir = DatabasesDir("/path/to/databases")
  ```

  **Android:**
  ```kotlin
  val databasesDir = context.databasesDir()
  ```

  **Jvm or Android:**
  ```kotlin
  val databasesDir = DatabasesDir(File("/path/to/databases"))
  val databasesDir = DatabasesDir(Path.of("/path/to/databases"))
  ```

- Define your `SQLiteMCDriver.Factory` configuration in `commonMain`

  **NOTE:** Realistically, your favorite singleton pattern or dependency injection 
  should be utilized here.

  ```kotlin
  // TLDR; 1 factory for each database file (will become evident later)
  val factory = SQLiteMCDriver.Factory(dbName = "test.db", schema = TestDatabase.Schema) {
      logger = { log -> println(log) }
      // Will redact key/rekey values, disable for debugging or playing (default: true)
      redactLogs = true
  
      // SqlDelight AfterVersion migration hooks
      afterVersions.add(AfterVersion(afterVersion = 2) { driver ->
          // do something
      })
      afterVersion(of = 2) { driver ->
          // do something
      }

      // Optional: Add PRAGMA statements to be executed
      // upon each connection opening.
      //
      // See >> https://www.sqlite.org/pragma.html
      pragmas {
          // both ephemeral and filesystem connections
          put("busy_timeout", 3_000.toString())
  
          // ephemeral connections only
          ephemeral.put("secure_delete", false.toString())
  
          // filesystem connections only
          filesystem.put("secure_delete", "fast")
      }

      // Can omit to simply go with the default DatabasesDir and
      // EncryptionConfig (ChaCha20)
      filesystem(databasesDir) {        
          encryption {
              // e.g. coming from SQLCipher library
              sqlCipher {
                  // v1()
                  // v2()
                  // v3()
                  v4()
                  // default()
              }
          }
      }
  }

  // NOTE: Suspension function "create" alternative available
  val driver1: SQLiteMcDriver = factory.createBlocking(Key.passphrase("password"))
  driver1.close()
  ```

- Easily spin up an ephemeral database for your configuration (no encryption)
  ```kotlin
  // NOTE: Suspension function "create" alternative available
  val inMemoryDriver = factory.createBlocking(opt = EphemeralOpt.IN_MEMORY)
  val namedDriver = factory.createBlocking(opt = EphemeralOpt.NAMED)
  val tempDriver = factory.createBlocking(opt = EphemeralOpt.TEMPORARY)
  inMemoryDriver.close()
  namedDriver.close()
  tempDriver.close()
  ```

- Easily change `Key`s
  ```kotlin
  // NOTE: Suspension function "create" alternative available
  val driver2 = factory.createBlocking(key = Key.passphrase("password"), rekey = Key.passphrase("new password"))
  driver2.close()

  // Remove encryption entirely by passing an empty key (i.e. Key.passphrase(""))
  val driver3 = factory.createBlocking(key = key.passphrase("new password"), rekey = Key.Empty)
  driver3.close()

  // Also supports use of RAW (already derived) keys and/or salt storage for SQLCipher & ChaCha20
  val salt = getOrCreate16ByteSalt("user abc")
  val derivedKey = derive32ByteKey(salt, "user secret password input")
  val rawKey = Key.raw(key = derivedKey, salt = salt, fillKey = true)

  val driver4 = factory.createBlocking(key = Key.Empty, rekey = rawKey)
  driver4.close()
  ```

- Easily migrate encryption configurations between software releases by defining a migrations block
  ```kotlin
  val factory = SQLiteMCDriver.Factory(dbName = "test.db", schema = TestDatabase.Schema) {
      logger = { log -> println(log) }
      redactLogs = false

      filesystem(databasesDir) {
          // NOTE: Never modify migrations, just leave them
          // for users who haven't opened your app in 5 years.
          encryptionMigrations {

              // Simply move your old encryption config up to a migration.
              //
              // If you _are_ migrating from SQLCipher library, note the
              // version of SQLCipher used the first time your app was
              // published with it. You will also need to define migrations
              // all the way back for each possible version (v1, v2, v3),
              // so that users who have not opened your app in a long time
              // can migrate from those versions as well.
              migrationFrom {
                  note = "Migration from SQLCipher library to sqlite-mc"
                  sqlCipher { v4() }
              }
          }
        
          encryption {
              sqlCipher { default() }
          }
      }
  }

  // Will try to open SQLCipher Default (legacy: 0).
  // 
  // On failure, Will try to open using migrations in reverse order of
  // what is expressed (i.e. SQLCipher-v4 (legacy: 4)).
  //
  // Once opened, will automatically migrate to SQLCipher Default (legacy: 0)
  // using the same Key that it opened with.
  val driverMigrate = factory.createBlocking(Key.passphrase("password"))
  driverMigrate.close()
  ```

- Share configurations between multiple factories
  ```kotlin
  val customChaCha20 = EncryptionConfig.new(other = null) {
      chaCha20 {
          default {
              // Define some non-default parameters if you wish
              kdfIter(250_000)
          }
      }
    
      // Other cipher choices that shouldn't be utilized for
      // new databases, but are there for migration purposes.
      //
      // rc4 { default() }
      // wxAES128 { default() }
      // wxAES256 { default() }
    
  }

  val migrationConfig = EncryptionMigrationConfig.new(other = null) {
      migrationFrom {
          note = "Migration from SQLCipher library to sqlite-mc"
          sqlCipher { v4() }
      }

      migrationFrom {
          note = "Migration from SQLCipher:default to ChaCha20"
          sqlCipher { default() }
      }
  }

  val sharedPragmas = PragmaConfig.new(other = null) {
      put("busy_timeout", 5_000.toString())
  }

  val sharedFilesystem = FilesystemConfig.new(databasesDir) {
      encryptionMigrations(migrationConfig)
      encryption(customChaCha20)
  }

  val factory1 = SQLiteMCDriver.Factory("first.db", DatabaseFirst.Schema) {
      pragmas(sharedPragmas)
      filesystem(sharedFilesystem)
  }
  val factory2 = SQLiteMCDriver.Factory("second.db", DatabaseSecond.Schema) {
      pragmas(sharedPragmas)
      filesystem(sharedFilesystem)
  }
  ```

### Jvm Supported Operating Systems

**NOTE:** `macOS` and `Windows` binaries are code signed.

|              | x86 | x86_64 | armv5 | armv6 | armv7 | arm64 | ppc64 |
|--------------|-----|--------|-------|-------|-------|-------|-------|
| Windows      | ✔   | ✔      |       |       |       |       |       |
| macOS        |     | ✔      |       |       |       | ✔     |       |
| Linux (libc) | ✔   | ✔      | ✔     | ✔     | ✔     | ✔     | ✔     |
| Linux (musl) | ✔   | ✔      |       |       |       | ✔     |       |
| FreeBSD      |     |        |       |       |       |       |       |

### Library Versioning

Versioning follows the following pattern of `SQLDelight` - `SQLite3MultipleCiphers` - `sqlite-mc sub version`

- `a.b.c` - `x.y.z` - `s`
- The `s` is a single digit to allow for bug fixes and the like
- An `s` of `0` indicates a "first release" for that pairing of `SQLDelight` and `SQLite3MultipleCiphers`
- Examples:
    - `2.0.0` - `1.6.4` - `0`
    - `2.0.0` - `1.6.4` - `1` (an update with `sqlite-mc` for `2.0.0-1.6.4`)
    - `2.0.1` - `1.6.4` - `0` (a minor version update with `SQLDelight`)
    - `2.0.1` - `1.6.5` - `0` (a minor version update with `SQLite3MultipleCiphers`)
    - `2.0.1` - `1.6.5` - `1` (an update with `sqlite-mc` for `2.0.1-1.6.5`)

### SQLite3MultipleCiphers Flags

[SQLite3MultipleCiphers][url-sqlitemc] is compiled with the following flags

**Jvm & Native:**

`SQLITE_HAVE_ISNAN=1`  
`HAVE_USLEEP=1`  
`SQLITE_ENABLE_COLUMN_METADATA=1`  
`SQLITE_CORE=1`  
`SQLITE_ENABLE_FTS3=1`  
`SQLITE_ENABLE_FTS3_PARENTHESIS=1`  
`SQLITE_ENABLE_FTS5=1`  
`SQLITE_ENABLE_RTREE=1`  
`SQLITE_ENABLE_STAT4=1`  
`SQLITE_ENABLE_DBSTAT_VTAB=1`  
`SQLITE_ENABLE_MATH_FUNCTIONS=1`  
`SQLITE_DEFAULT_MEMSTATUS=0`  
`SQLITE_DEFAULT_FILE_PERMISSIONS=0666`  
`SQLITE_MAX_VARIABLE_NUMBER=250000`  
`SQLITE_MAX_MMAP_SIZE=0`  
`SQLITE_MAX_LENGTH=2147483647`  
`SQLITE_MAX_COLUMN=32767`  
`SQLITE_MAX_SQL_LENGTH=1073741824`  
`SQLITE_MAX_FUNCTION_ARG=127`  
`SQLITE_MAX_ATTACHED=125`  
`SQLITE_MAX_PAGE_COUNT=4294967294`  
`SQLITE_DQS=0`   
`CODEC_TYPE=CODEC_TYPE_CHACHA20`  
`SQLITE_ENABLE_EXTFUNC=1`  
`SQLITE_ENABLE_REGEXP=1`  
`SQLITE_TEMP_STORE=2`  
`SQLITE_USE_URI=1`  

**Jvm**

`SQLITE_THREADSAFE=1`  

**Native:**

`SQLITE_THREADSAFE=2`  
`SQLITE_OMIT_LOAD_EXTENSION`

<details>
    <summary>Reason</summary>

```
2 (Multi-Threaded) is the default for Darwin
targets, but on JVM it is using 1 (Serialized).

SQLDelight's NativeSqliteDriver utilizes thread pools
and nerfs any benefit that Serialized would offer, so.

This *might* change in the future if migrating away from
SQLDelight's NativeSqliteDriver and SQLiter.

Omission of the load extension code is only able to be
set for Native, as Jvm requires the code to remain in
order to link with the JNI interface. Extension loading
is disabled by default for Jvm, but the C code must stay
in order to mitigate modifying the Java codebase.
```

</details>

**Darwin:**

`SQLITE_ENABLE_API_ARMOR`  
`SQLITE_OMIT_AUTORESET`  

<details>
    <summary>Reason</summary>

```
Options that SQLite is compiled with on
Darwin devices. macOS 10.11.6+, iOS 9.3.5+
```

</details>

**iOS, tvOS, watchOS:**

`SQLITE_ENABLE_LOCKING_STYLE=0`  

<details>
    <summary>Reason</summary>

```
D.Richard Hipp (SQLite architect) suggests for non-macOS: 

"The SQLITE_ENABLE_LOCKING_STYLE thing is an apple-only 
extension that boosts performance when SQLite is used 
on a network filesystem. This is important on macOS because 
some users think it is a good idea to put their home 
directory on a network filesystem.

I'm guessing this is not really a factor on iOS."
```

</details>

### Get Started

<!-- TAG_VERSION -->

1. Remove `SQLDelight` gradle plugin and drivers from your project
2. Apply the `sqlite-mc` gradle plugin.
   ```kotlin
   plugins {
       // Provides the SQLDelight gradle plugin automatically and applies it
       id("io.toxicity.sqlite-mc") version("2.0.0-1.7.2-0")
   }

   // Will automatically:
   //  - Configure the latest SQLite dialect
   //  - Add the sqlite-mc driver dependency
   //  - Link native targets for provided SQLite3MultipleCiphers binaries
   sqliteMC {
       databases {
           // Configure just like you would the SQLDelight plugin
       }
   }
   ```
3. If you have Android unit tests
   ```kotlin
   dependencies {
       // For android unit tests (NOT instrumented)
       //
       // This is simply the desktop binary resources needed for
       // JDBC to operate locally on the machine.
       testImplementation("io.toxicity.sqlite-mc:android-unit-test:2.0.0-1.7.2-0")
   }
   ```

<!-- TAG_VERSION -->
[badge-latest-release]: https://img.shields.io/badge/latest--release-2.0.0--1.7.2--0-blue.svg?style=flat
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-kotlin]: https://img.shields.io/badge/kotlin-1.9.21-blue.svg?logo=kotlin
[badge-coroutines]: https://img.shields.io/badge/coroutines-1.7.3-blue.svg?logo=kotlin
[badge-encoding]: https://img.shields.io/badge/encoding-2.1.0-blue.svg?style=flat
[badge-immutable]: https://img.shields.io/badge/immutable-0.1.0-blue.svg?style=flat
[badge-sqldelight]: https://img.shields.io/badge/SQLDelight-2.0.0-blue.svg?style=flat
[badge-sqlite]: https://img.shields.io/badge/SQLite3-3.43.2-blue.svg?style=flat
[badge-sqlitemc]: https://img.shields.io/badge/SQLite3MultipleCiphers-1.7.2-blue.svg?style=flat
[badge-sqliter]: https://img.shields.io/badge/SQLiter-1.2.3-blue.svg?style=flat
[badge-sqlitejdbc]: https://img.shields.io/badge/sqlite--jdbc-3.43.2.2-blue.svg?style=flat

<!-- TAG_PLATFORMS -->
[badge-platform-android]: http://img.shields.io/badge/-android%20[minSdk%2021]-6EDB8D.svg?style=flat
[badge-platform-jvm]: http://img.shields.io/badge/-jvm-DB413D.svg?style=flat
[badge-platform-js]: http://img.shields.io/badge/-js-F8DB5D.svg?style=flat
[badge-platform-js-node]: https://img.shields.io/badge/-nodejs-68a063.svg?style=flat
[badge-platform-linux]: http://img.shields.io/badge/-linux-2D3F6C.svg?style=flat
[badge-platform-macos]: http://img.shields.io/badge/-macos-111111.svg?style=flat
[badge-platform-ios]: http://img.shields.io/badge/-ios-CDCDCD.svg?style=flat
[badge-platform-tvos]: http://img.shields.io/badge/-tvos-808080.svg?style=flat
[badge-platform-watchos]: http://img.shields.io/badge/-watchos-C0C0C0.svg?style=flat
[badge-platform-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat
[badge-platform-windows]: http://img.shields.io/badge/-windows-4D76CD.svg?style=flat
[badge-support-android-native]: http://img.shields.io/badge/support-[AndroidNative]-6EDB8D.svg?style=flat
[badge-support-apple-silicon]: http://img.shields.io/badge/support-[AppleSilicon]-43BBFF.svg?style=flat
[badge-support-js-ir]: https://img.shields.io/badge/support-[js--IR]-AAC4E0.svg?style=flat
[badge-support-linux-arm]: http://img.shields.io/badge/support-[LinuxArm]-2D3F6C.svg?style=flat

[url-latest-release]: https://github.com/toxicity-io/sqlite-mc/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0.txt

[url-kotlin]: https://kotlinlang.org
[url-coroutines]: https://github.com/Kotlin/kotlinx.coroutines
[url-encoding]: https://github.com/05nelsonm/encoding
[url-immutable]: https://github.com/05nelsonm/immutable
[url-sqldelight]: https://github.com/cashapp/sqldelight
[url-sqlite]: https://sqlite.org
[url-sqlitemc]: https://github.com/utelle/SQLite3MultipleCiphers
[url-sqliter]: https://github.com/touchlab/SQLiter
[url-sqlitejdbc]: https://github.com/xerial/sqlite-jdbc
