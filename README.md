<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="112" alt="lucy app icon">
</p>

<h1 align="center">lucy</h1>

<p align="center">A minimalist Android reader for manga and manhwa.</p>

lucy is a native Android app built with Kotlin and Jetpack Compose. It follows the black-and-white look of the lucy web reader and adds an offline catalog, multiple chapter sources, and daily background updates.

## Download

Get the latest signed APK from [Releases](https://github.com/yowayui/lucyapp/releases/latest).

Requirements: Android 7.0 (API 24) or newer.

Download the APK, open it, and allow installation from unknown sources when Android asks. Newer releases install over the old app as long as the signing key stays the same.

## Features

- **Home**: featured carousel, daily popular ranking, and latest updates in a layout that matches the web reader
- **Offline catalog**: browse and search about 200,000 series from the bundled catalog, even without a connection
- **Reader**: webtoon mode with vertical scrolling or paged mode with horizontal swiping, plus automatic next chapter
- **Library**: bookmarks, reading history, and per-chapter read markers
- **Daily updates**: background checks for bookmarked and recently read series, with notifications
- **Settings**: reader mode, theme, background update toggle, manual update check, and social links
- **Themes**: light and dark, following the system setting by default
- **No account**: bookmarks, history, and settings stay on the device

## About the catalog

The APK ships with an offline catalog of series metadata. Chapter images and pages are fetched from public sources when you open a series or chapter. The app does not host or upload manga content. Titles, images, and chapters belong to their respective owners and sites.

## Tech stack

- Kotlin and Jetpack Compose with Material 3
- WorkManager for daily background updates
- OkHttp and Jsoup for source requests and HTML parsing
- Coil for image loading
- SQLite for the bundled catalog and local user data

## License

All rights reserved. See [LICENSE](LICENSE) for details.
