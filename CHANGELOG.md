# CHANGELOG

## Version 2.0.2-2.0.3-0 (2025-02-14)
 - Updates `SQLite3` to `3.49.0` [[#136]][136]
     - `sqlite-jdbc` to `3.49.0.0`
     - `SQLite3MultipleCiphers` to `2.0.3`

## Version 2.0.2-2.0.2-0 (2025-01-17)
 - Updates `SQLite3` to `3.48.0` [[#133]][133]
     - `sqlite-jdbc` to `3.48.0.0`
     - `SQLite3MultipleCiphers` to `2.0.2`

## Version 2.0.2-2.0.1-0 (2025-01-16)
 - Updates `SQLite3` to `3.47.2` [[#131]][131]
     - `sqlite-jdbc` to `3.47.2.0`
     - `SQLite3MultipleCiphers` to `2.0.1`
 - Updates dependencies [[#131]][131]
     - `encoding` to `2.3.1`

## Version 2.0.2-1.9.1-0 (2024-12-09)
 - Updates `SQLite3` to `3.47.1` [[#130]][130]
     - `sqlite-jdbc` to `3.47.1.0`
     - `SQLite3MultipleCiphers` to `1.9.1`

## Version 2.0.2-1.9.0-0 (2024-10-26)
 - Updates `SQLite3` to `3.47.0` [[#127]][127]
     - `sqlite-jdbc` to `3.47.0.0`
     - `SQLite3MultipleCiphers` to `1.9.0`
 - Fixes multiplatform metadata manifest `unique_name` parameter for all source sets to be truly unique [[#128]][128]
 - Updates Android/JVM `.kotlin_module` with truly unique file name [[#128]][128]
 - Updates dependencies [[#128]][128]
     - `androidx.startup` to `1.2.0`
     - `encoding` to `2.2.2`
     - `immutable` to `0.1.4`

## Version 2.0.2-1.8.7-0 (2024-08-21)
 - Updates `SQLite3` to `3.46.1` [[#123]][123]
     - `sqlite-jdbc` to `3.46.1.0`
     - `SQLite3MultipleCiphers` to `1.8.7`

## Version 2.0.2-1.8.6-0 (2024-06-15)
 - Updates `SQLite3MultipleCiphers` to `1.8.6` [[#121]][121]
 - Updates dependencies [[#121]][121]
     - `immutable` to `0.1.3`
     - `kotlin` to `1.9.24`

## Version 2.0.2-1.8.5-0 (2024-05-27)
 - Updates `SQLite3` to `3.46.0` [[#119]][119]
     - `sqlite-jdbc` to `3.46.0.0`
     - `SQLite3MultipleCiphers` to `1.8.5`
 - Updates dependencies [[#119]][119]
     - `kotlinx-coroutines-core` to `1.8.1`

## Version 2.0.2-1.8.4-0 (2024-04-30)
 - Updates `SQLite3` to `3.45.2` [[#114]][114]
     - `sqlite-jdbc` to `3.45.2.0`
     - `SQLite3MultipleCiphers` to `1.8.4`
 - Updates dependencies [[#115]][115]
     - `encoding` to `2.2.1`
     - `immutable` to `0.1.2`
     - `kotlin` to `1.9.23`
     - `kotlinx-coroutines-core` to `1.8.0`
     - `sqldelight` to `2.0.2`

## Version 2.0.1-1.8.3-0 (2024-02-13)
 - Updates `SQLite3` to `3.45.1` [[#110]][110]
     - `sqlite-jdbc` to `3.45.1.0`
     - `SQLite3MultipleCiphers` to `1.8.3`

## Version 2.0.1-1.8.2-0 (2024-01-19)
 - Updates `SQLite3` to `3.45.0` [[#106]][106] [[#107]][107]
     - `sqlite-jdbc` to `3.45.0.0`
         - Updates [build-env][url-build-env] from `0.1.0` to `0.1.3`
         - Migrates `linux-ppc64` build from `dockcross` to [build-env][url-build-env]
     - `SQLite3MultipleCiphers` to `1.8.2`
     - `sqldelight` to `2.0.1`
         - Increases minimum JDK version for `gradle-plugin` from `11` to `17`
         - Adds support for `watchosDeviceArm64`
 - Adds support for cipher `ascon128` [[#108]][108]

## Version 2.0.0-1.7.2-0 (2024-01-18)
 - Adds usage of [immutable][url-immutable] library for publicly exposed collections/maps [[#103]][103]

## Version 2.0.0-1.7.2-0-beta01 (2023-12-02)
 - Updates dependencies [[#85]][85]
     - `kotlin` to `1.9.21`
     - `encoding` to `2.1.0`
 - Updates `sqlite-jdbc` to `3.43.2.2` [[#94]][94] 
 - `Linux-Android` binaries for `sqlite-jdbc` are no longer dropped when repackaging
   for `Jvm` to support applications running via `Termux` [[#86]][86]
 - Modifies `sqlite-jdbc` build to use [build-env][url-build-env] docker containers [[#91]][91]
     - Fixes Windows build reproducibility
     - Allows for reducing Android `minSdk` from `23` to `21`
     - Linux libc binaries are compiled against older version of `glibc` (specifically, `2.23`)
       for some supported targets than what [dockcross][url-dockcross] uses.
 - Adds `androidx.startup` dependency to obtain default `databases` directory for
   Android Runtime [[#95]][95]
 - Lowers Android `minSdk` to `21` [[#96]][96]

## Version 2.0.0-1.7.2-0-alpha01 (2023-10-13)
 - Initial `alpha` release

[url-build-env]: https://github.com/05nelsonm/build-env
[url-dockcross]: https://github.com/dockcross/dockcross
[url-immutable]: https://github.com/05nelsonm/immutable

[85]: https://github.com/toxicity-io/sqlite-mc/pull/85
[86]: https://github.com/toxicity-io/sqlite-mc/pull/86
[91]: https://github.com/toxicity-io/sqlite-mc/pull/91
[94]: https://github.com/toxicity-io/sqlite-mc/pull/94
[95]: https://github.com/toxicity-io/sqlite-mc/pull/95
[96]: https://github.com/toxicity-io/sqlite-mc/pull/96
[103]: https://github.com/toxicity-io/sqlite-mc/pull/103
[106]: https://github.com/toxicity-io/sqlite-mc/pull/106
[107]: https://github.com/toxicity-io/sqlite-mc/pull/107
[108]: https://github.com/toxicity-io/sqlite-mc/pull/108
[110]: https://github.com/toxicity-io/sqlite-mc/pull/110
[114]: https://github.com/toxicity-io/sqlite-mc/pull/114
[115]: https://github.com/toxicity-io/sqlite-mc/pull/115
[119]: https://github.com/toxicity-io/sqlite-mc/pull/119
[121]: https://github.com/toxicity-io/sqlite-mc/pull/121
[123]: https://github.com/toxicity-io/sqlite-mc/pull/123
[127]: https://github.com/toxicity-io/sqlite-mc/pull/127
[128]: https://github.com/toxicity-io/sqlite-mc/pull/128
[130]: https://github.com/toxicity-io/sqlite-mc/pull/130
[131]: https://github.com/toxicity-io/sqlite-mc/pull/131
[133]: https://github.com/toxicity-io/sqlite-mc/pull/133
[136]: https://github.com/toxicity-io/sqlite-mc/pull/136
