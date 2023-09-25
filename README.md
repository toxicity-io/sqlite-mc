# sqlite-mc-driver
[![badge-license]][url-license]
[![badge-latest-release]][url-latest-release]

[![badge-kotlin]][url-kotlin]
[![badge-coroutines]][url-coroutines]
[![badge-encoding]][url-encoding]
[![badge-sql-delight]][url-sql-delight]
[![badge-sql-mc]][url-sql-mc]
[![badge-sql-jdbc]][url-sql-jdbc]

![badge-platform-android]
![badge-platform-jvm]
<!--
![badge-platform-js]
![badge-platform-js-node]
![badge-platform-linux]
![badge-platform-macos]
![badge-platform-ios]
![badge-platform-tvos]
![badge-platform-watchos]
![badge-platform-wasm]
![badge-platform-windows]
![badge-support-android-native]
![badge-support-apple-silicon]
![badge-support-js-ir]
![badge-support-linux-arm]
![badge-support-linux-mips]
-->

An [SQLDelight][url-sql-delight] driver with a slick DSL that uses [SQLite3MultipleCiphers][url-sql-mc] for 
database encryption.

### Usage

Define `DatabasesDir`

**Common:**
```kotlin
// Use system default location for the given platform
val databasesDir = DatabasesDir()
val databasesDir = DatabasesDir(null) // null
val databasesDir = DatabasesDir("     ") // blank

// Specify a path
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

Define your `SQLiteMCDriver.Factory` configuration in `commonMain`

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

    // Can omit to simply go with the default
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

Easily spin up an ephemeral database for your configuration (no encryption)
```kotlin
val inMemoryDriver = factory.createBlocking(opt = EphemeralOpt.IN_MEMORY)
val namedDriver = factory.createBlocking(opt = EphemeralOpt.NAMED)
val tempDriver = factory.createBlocking(opt = EphemeralOpt.TEMPORARY)
inMemoryDriver.close()
namedDriver.close()
tempDriver.close()
```

Easily change `Key`s
```kotlin
// NOTE: Suspension function "create" alternative available
val driver2 = factory.createBlocking(
    key = Key.passphrase("password"),
    rekey = Key.passphrase("new password"),
)
driver2.close()

// Also supports use of RAW (already derived) keys and/or salt storage for SQLCipher & ChaCha20
val (derivedKey, salt) = deriveMy32ByteKeyAnd16ByteSalt("secret password") // however you want to do it
val rawKey = Key.raw(key = derivedKey, salt = salt, fillKey = true)

val driver3 = factory.createBlocking(key = Key.passphrase("new password"), rekey = rawKey)
```

Interact with the `SQLite3MultipleCiphers` interface via queries
```kotlin
val mcQueries = MCConfigQueries.from(driver3)

// Whether you use a Key.passphrase or Key.raw, after encrypting
// the database it's always a good idea to retrieve the
// salt and store elsewhere, in case it is needed to recover
// data or something. Without it, you are 100% screwed even
// if you know the passphrase.
val salt: String? = mcQueries.cipherSalt()
// 32 character string (16 bytes encoded in base 16 (hex))

// Checkout some of the SQLite3MultipleCipher current settings
// for each of it's supported ciphers
val rc4PageSize: Int? = mcQueries.cipherParam(Cipher.RC4, Pragma.MC.LEGACY_PAGE_SIZE)
val sqlCipherHmacPngo: HmacPngo? = mcQueries.cipherParam(Cipher.SQLCIPHER, Pragma.MC.HMAC_PNGO)
val chaCha20KdfIter: Int? = mcQueries.cipherParam(Cipher.CHACHA20, Pragma.MC.KDF_ITER)
val aes128PageSize: Int? = mcQueries.cipherParam(Cipher.AES128CBC, Pragma.MC.LEGACY_PAGE_SIZE)
val aes256KdfIter: Int? = mcQueries.cipherParam(Cipher.AES256CBC, Pragma.MC.KDF_ITER)

// more functions, checkout the code.
```

Easily migrate encryption configurations between software releases by simply defining a migrations block 

```kotlin
val factory = SQLiteMCDriver.Factory(dbName = "test.db", schema = TestDatabase.Schema) {
    logger = { log -> println(log) }
    redactLogs = false

    filesystem(databasesDir) {
        // NOTE: Never modify migrations, just leave them
        // for users who haven't opened your app in 5 years.
        encryptionMigrations {

            // Simply move your old encryption config up to a migration.
            migrationFrom {
                note = "Migration from SQLCipher library to sqlite-mc-driver"
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

Share configurations between multiple factories

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
        note = "Migration from SQLCipher library to sqlite-mc-driver"
        sqlCipher { v4() }
    }

    migrationFrom {
        note = "Migration from SQLCipher:default to ChaCha20"
        sqlCipher { default() }
    }
}

val sharedConfig = FilesystemConfig.new(databasesDir) {
    encryptionMigrations(migrationConfig)
    encryption(customChaCha20)
}

val factory1 = SQLiteMCDriver.Factory("first.db", DatabaseFirst.Schema) {
    filesystem(sharedConfig)
}
val factory2 = SQLiteMCDriver.Factory("second.db", DatabaseSecond.Schema) {
    filesystem(sharedConfig)
}
```

### Get Started

<!-- TAG_VERSION -->
[badge-latest-release]: https://img.shields.io/badge/latest--release-0.1.0--alpha01-blue.svg?style=flat
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-kotlin]: https://img.shields.io/badge/kotlin-1.8.22-blue.svg?logo=kotlin
[badge-coroutines]: https://img.shields.io/badge/coroutines-1.7.3-blue.svg?logo=kotlin
[badge-encoding]: https://img.shields.io/badge/encoding-2.0.0-blue.svg?style=flat
[badge-sql-delight]: https://img.shields.io/badge/SQLDelight-2.0.0-blue.svg?style=flat
[badge-sql-mc]: https://img.shields.io/badge/SQLite3MultipleCiphers-1.6.4-blue.svg?style=flat
[badge-sql-jdbc]: https://img.shields.io/badge/sqlite--jdbc-3.43.0.0-blue.svg?style=flat

<!-- TAG_PLATFORMS -->
[badge-platform-android]: http://img.shields.io/badge/-android%20[minSdk%2023]-6EDB8D.svg?style=flat
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
[badge-support-linux-mips]: http://img.shields.io/badge/support-[LinuxMIPS]-2D3F6C.svg?style=flat

[url-latest-release]: https://github.com/toxicity-io/sqlite-mc-driver/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0.txt

[url-kotlin]: https://kotlinlang.org
[url-coroutines]: https://github.com/Kotlin/kotlinx.coroutines
[url-encoding]: https://github.com/05nelsonm/encoding
[url-sql-delight]: https://github.com/cashapp/sqldelight
[url-sql-mc]: https://github.com/utelle/SQLite3MultipleCiphers
[url-sql-jdbc]: https://github.com/xerial/sqlite-jdbc

