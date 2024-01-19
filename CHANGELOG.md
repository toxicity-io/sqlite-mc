# CHANGELOG

## Version 2.0.1-1.8.2-0 (2024-01-19)
 - Updates `SqlDelight` to `2.0.1` [[#106]][106]
     - Increases minimum JDK version for `gradle-plugin` from `11` to `17`
 - Updates `sqlite-jdbc` to `3.45.0.0` [[#106]][106] [[#107]][107]
     - Updates [build-env][url-build-env] from `0.1.0` to `0.1.3`
     - Migrates `linux-ppc64` build from `dockcross` to [build-env][url-build-env]
 - Updates `SQLite3MultipleCiphers` to `1.8.2` [[#106]][106] [[#107]][107]
 - Adds support for cipher `ascon128` [[#108]][108]

## Version 2.0.0-1.7.2-0 (2024-01-18)
 - Adds usage of [immutable][url-immutable] library for publicly exposed collections/maps [[#103]][103]

## Version 2.0.0-1.7.2-0-beta01 (2023-12-02)
 - Updates `kotlin` to `1.9.21` [[#85]][85]
 - Updates `encoding` to `2.1.0` [[#85]][85]
 - `Linux-Android` binaries for `sqlite-jdbc` are no longer dropped when repackaging
   for `Jvm` to support applications running via `Termux` [[#86]][86]
 - Modifies `sqlite-jdbc` build to use [build-env][url-build-env] docker containers [[#91]][91]
     - Fixes Windows build reproducibility
     - Allows for reducing Android `minSdk` from `23` to `21`
     - Linux libc binaries are compiled against older version of `glibc` (specifically, `2.23`)
       for some supported targets than what [dockcross][url-dockcross] uses.
 - Updates `sqlite-jdbc` to `3.43.2.2` [[#94]][94]
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
