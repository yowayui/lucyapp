# lucy

Native Android reader for doujinshi, manga, and manhwa. Built with Kotlin and Jetpack Compose.

## Download

The signed APK is published on the [Releases](https://github.com/yowayui/lucyapp/releases/latest) page.

## Features

- Daily background chapter checks for bookmarked and recently read series
- Offline catalog with search, browse, and a daily featured selection
- Reader modes for manga, manhwa, and webtoon
- Library, reading history, and read markers
- Light and dark themes

## Build

Requirements: JDK 21 and Android SDK 35.

```sh
./gradlew assembleRelease
```

The release variant currently uses the local debug keystore. Replace the `signingConfig` in `app/build.gradle.kts` with a private keystore before publishing your own builds.

The offline catalog asset `app/src/main/assets/lucy_catalog.db` is not included because of its size (about 109 MB). The build still succeeds without it, but catalog screens will have no data at runtime. Generate the file before building if you need a working catalog.

## Connect

- Instagram: [@y1peh](https://www.instagram.com/y1peh)
- Email: [yuiwoip@gmail.com](mailto:yuiwoip@gmail.com)

## License

MIT
