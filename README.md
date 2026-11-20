<p align="center">
  <img src="docs/icon.png" alt="InkPal icon" width="112" height="112">
</p>

<h1 align="center">InkPal</h1>

<p align="center">
  <b>A calm, black-and-white reader for Android.</b><br>
  Reads the EPUB and PDF books on your phone, plays your M4B audiobooks, remembers your place
  and keeps a quiet history of everything you read. No account, no ads, no internet.
</p>

<p align="center">
  <a href="https://github.com/SarveshP16/InkPal/releases/latest/download/InkPal.apk"><img src="https://img.shields.io/badge/Download-APK-111111?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"></a>
  <a href="https://github.com/SarveshP16/InkPal/releases"><img src="https://img.shields.io/github/downloads/SarveshP16/InkPal/total?style=for-the-badge&color=111111&label=downloads" alt="Total downloads"></a>
</p>

<p align="center">
  <a href="https://github.com/SarveshP16/InkPal/releases/latest"><img src="https://img.shields.io/github/v/release/SarveshP16/InkPal?label=release&color=111111" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-111111?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-111111?logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-111111" alt="MIT licence"></a>
  <a href="https://ko-fi.com/crystaxit"><img src="https://img.shields.io/badge/Ko--fi-support%20me-FF5E5B?logo=ko-fi&logoColor=white" alt="Support on Ko-fi"></a>
</p>

<p align="center">
  <img src="docs/screenshots/01-library.png" alt="Library" width="200">
  <img src="docs/screenshots/02-reader.png" alt="Reading a book" width="200">
  <img src="docs/screenshots/05-sepia.png" alt="Sepia theme" width="200">
  <img src="docs/screenshots/09-audiobook.png" alt="Audiobook player" width="200">
</p>

<p align="center"><sub>Screenshots use public-domain sample books.</sub></p>

## Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Download and install](#download-and-install)
- [How to use it](#how-to-use-it)
- [Privacy and battery](#privacy-and-battery)
- [Build from source](#build-from-source)
- [Support the project](#support-the-project)
- [Licence](#licence)

## Features

### Your library
- **Your own files.** Pick the folders on your phone that hold your books; InkPal finds every `.epub`, `.pdf` and `.m4b` inside them, subfolders too, and picks up new ones each time you open it.
- **Real covers, titles and authors** read from inside each file. Books without a cover get a clean typographic one.
- **Continue** cards for the book and the audiobook you're in the middle of, then **All / Reading / Audiobooks / Finished** shelves.
- **Search** by title or author, and **sort** by recently read, title or author.
- **Book details**: progress, size, page count or length, folder, reading sessions, reset progress, remove from library (the file stays on your phone).

### Reading EPUBs
- **Real pages**, not endless scrolling. Tap the right edge, swipe or press **volume down** to turn the page; tap the left edge or **volume up** to go back.
- **Tap the middle** for the menu: a slider to jump anywhere, previous / next chapter, contents, bookmarks and search.
- **Bookmarks** on any page, with the first words of the page so you know which is which. A small ribbon marks bookmarked pages.
- **Search inside the book**: every match with its context, grouped by chapter; tap one to jump there with the word highlighted.
- **Footnotes pop up** over the page instead of sending you to the back of the book. Links inside the book jump there with a **Back** chip to return; web links open in your browser.
- **Text settings**: size, font (**Literata**, sans, or the book's own), line spacing, margins and reader-only brightness. Changes apply instantly and keep your place.
- **White**, **Sepia** or **True black** theme. True black lights only the text, which saves power on OLED screens.

### Reading PDFs
- One page at a time, turned the same way as EPUBs: tap, swipe or volume keys.
- **Pinch or double-tap to zoom**, drag to look around; the page is re-drawn sharper when you zoom in.
- **Sepia** and **True black** recolour the page too.
- Bookmarks, a page slider, your place and reading sessions work as for EPUBs.

### Listening to audiobooks
- **M4B audiobooks** with their **chapters**, read from the file itself.
- Plays with the **screen off**, with controls in the notification and on the lock screen.
- **Skip 30 seconds** back or forward, previous / next chapter, a chapter list, and **speed** from 0.8× to 2×.
- **Sleep timer**: 15, 30, 45 or 60 minutes, or the end of the chapter.
- A **mini player** sits above the tabs while a book is playing.

### History, progress and wishlist
- **Your place is saved** on every page turn, and every few seconds while listening.
- **Reading and listening sessions** are logged automatically: when, for how long and how far.
- **History** shows time read this week, books finished this year, your streak, a day-by-day activity list and everything you've finished.
- **Mark as finished** from the book page, the reader menu, the player or the end-of-book screen, with undo.
- **Wishlist**: keep a list of books you want to read with the author and a note. Tick them off when you get them, and see when one is already in your library.

## Screenshots

| Library | Reader | Reader menu | Text settings |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/01-library.png" width="200" alt="Library"> | <img src="docs/screenshots/02-reader.png" width="200" alt="Reader"> | <img src="docs/screenshots/03-reader-menu.png" width="200" alt="Reader menu"> | <img src="docs/screenshots/04-text-settings.png" width="200" alt="Text settings"> |

| Sepia | Search | Footnote | PDF |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/05-sepia.png" width="200" alt="Sepia theme"> | <img src="docs/screenshots/06-search.png" width="200" alt="Search in a book"> | <img src="docs/screenshots/07-footnote.png" width="200" alt="Footnote pop-up"> | <img src="docs/screenshots/08-pdf.png" width="200" alt="PDF page"> |

| Audiobook | Wishlist | History | Book details |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/09-audiobook.png" width="200" alt="Audiobook player"> | <img src="docs/screenshots/10-wishlist.png" width="200" alt="Wishlist"> | <img src="docs/screenshots/11-history.png" width="200" alt="History"> | <img src="docs/screenshots/12-details.png" width="200" alt="Book details"> |

## Download and install

1. Download **[InkPal.apk](https://github.com/SarveshP16/InkPal/releases/latest/download/InkPal.apk)** from the [latest release](https://github.com/SarveshP16/InkPal/releases/latest).
2. Open it on your phone. If Android asks, allow your browser or file manager to install unknown apps.
3. Open InkPal and tap **Choose a folder**.

Requires Android 8.0 or newer. Every release is signed with the same key, so new versions install over the old one and keep your library.

## How to use it

| To… | Do this |
|---|---|
| Add books | **Choose a folder** on first launch, or **+** on the library, or **Settings → Add folder** |
| Turn the page | Tap the right edge, swipe left, or press volume down |
| Go back a page | Tap the left edge, swipe right, or press volume up |
| Open the menu | Tap the middle of the page |
| Bookmark a page | Menu → ribbon icon; see them in **Bookmarks** |
| Search the book | Menu → magnifier |
| Read a footnote | Tap its number |
| Change the text or theme | Menu → **Aa** |
| Zoom a PDF | Pinch, or double-tap |
| Listen | Open an `.m4b`; use the mini player above the tabs to get back to it |
| Finish a book | Read or listen to the end, or **Mark finished** |
| Remember a book for later | **Wishlist → +** |

## Privacy and battery

- **No permission prompts.** InkPal only sees the folders you pick, through Android's own folder picker. It has no internet access, no account, no analytics and no ads. The only permissions it declares are the three Android needs for audiobooks to keep playing with the screen off (foreground media service and wake lock); none of them asks you anything.
- **Everything stays on your phone**: your library, progress, bookmarks, wishlist and history are stored in the app's private storage.
- **Gentle on the battery.** Pages don't animate or redraw between turns, the screen can stay on while you read and turns off after a few idle minutes, and the true-black theme saves power on OLED screens.

## Build from source

You need JDK 21 and the Android SDK (platform 36).

```bash
git clone https://github.com/SarveshP16/InkPal.git
cd InkPal
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. The debug build installs alongside the release app as **InkPal (debug)**, with its own data.

To build a signed release, put a `keystore.properties` file in the project root (it is git-ignored):

```properties
storeFile=../your-release-key.jks
storePassword=…
keyAlias=…
keyPassword=…
```

then run `./gradlew :app:assembleRelease`.

**Stack:** Kotlin, Jetpack Compose and Material 3; a WebView-based paged EPUB engine with jsoup for contents and search; Android's PdfRenderer for PDFs; Media3 (ExoPlayer and MediaSession) for audiobooks; kotlinx.serialization for local storage.

## Support the project

InkPal is free and always will be. If it makes your reading a little nicer, you can buy me a coffee:

<a href="https://ko-fi.com/crystaxit"><img src="https://img.shields.io/badge/Ko--fi-support%20me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Support on Ko-fi"></a>

Bug reports and ideas are welcome in [Issues](https://github.com/SarveshP16/InkPal/issues).

## Licence

[MIT](LICENSE) © 2026 Sarvesh Pandit

The Literata typeface is © The Literata Project Authors and used under the [SIL Open Font License 1.1](licenses/Literata-OFL.txt).
