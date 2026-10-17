# TransLens

A minimal Android browser that translates text inside images — web pages, manga, photos and the
camera — and paints the translation over the original, Google Lens style.

## Features

- **Live translation** — tap the translate button and the visible page is translated in place.
  It follows scrolling and translates new content as it appears (great for manga and webtoons).
  Press and hold the button to peek at the original.
- **Manga aware** — vertical Japanese text is read right-to-left, speech bubbles are detected and
  translated as whole sentences, and translations are drawn inside the bubble.
- **Live camera** — point at signs, menus or labels; the shutter freezes the translated frame.
- **Photos** — translate any image from the gallery.
- **Languages** — reads Latin-script languages, Chinese, Japanese, Korean and Devanagari
  (Hindi, Marathi, Nepali); translates into 29 languages (English by default).
- **Online or offline** — the free Google Translate web endpoint for best quality, falling back to
  ML Kit on-device models (~30 MB per language, downloaded once) without internet.
- **Ad blocker** — blocks ad, tracker and pop-up domains using the AdGuard DNS filter, AdAway,
  StevenBlack hosts, or any list you add (AdGuard/uBlock `||domain^` rules or hosts files).
- **Browser basics** — find in page, desktop site, share, downloads, bookmarks, history,
  search engine choice, dark websites, text size, clear browsing data.

## How it works

1. The visible area is captured (WebView render, camera frame or photo).
2. ML Kit text recognition runs every script recognizer and keeps the one that fits the page.
   Lines are grouped into paragraphs; vertical columns are grouped into speech bubbles and re-read.
3. The language of each block is detected and the blocks are translated in one batch.
4. Each translation is drawn over a patch in the sampled background color, sized to fit.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew connectedDebugAndroidTest   # OCR + translation pipeline tests on a device/emulator
```

Release builds are signed with the key in `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_ALIAS` /
`KEY_PASSWORD` when set, otherwise with the local debug key.

## Security notes

- Offline OCR, language detection, and translation use on-device ML Kit models.
- Online translation is optional. When enabled, recognized text is sent over
  HTTPS to Google Translate's public web endpoint. Do not use online mode for
  sensitive text that should not leave the device.
- Browsing history, translation cache, and downloaded filter lists are stored
  in the app's private storage. Clear browsing data from Settings before
  sharing a device or a debug build.
- Filter lists are downloaded from their configured URLs and are treated as
  untrusted input. Only use sources you trust, and review custom URLs before
  enabling them.
- Never commit keystores, passwords, tokens, private test data, or generated
  build output. Keep release signing values in environment variables or a
  local, untracked configuration.

To report a security vulnerability, please avoid posting working exploit
details publicly. Contact the maintainers privately through the repository's
security contact or GitHub security reporting tools.

## Collaboration

1. Create a focused branch from `main`.
2. Make the smallest change that solves the problem and add or update tests
   when behavior changes.
3. Run the relevant checks locally, at minimum `./gradlew testDebugUnitTest`.
4. Open a pull request with a clear summary, testing notes, and any privacy or
   security impact.

Please keep pull requests focused, follow the existing Kotlin and Compose
style, and do not include secrets, personal data, or unrelated formatting
changes. Bug reports should include reproducible steps, device or emulator
details, Android version, and relevant logs with private data removed.

## License

TransLens is licensed under the GNU Affero General Public License v3.0 or
later. See [LICENSE](LICENSE) for the full license text.

## Project layout

```
app/src/main/java/com/example/
  data/ocr/          OCR, script detection, paragraph + speech-bubble grouping
  data/translation/  online/offline translation and language detection
  data/adblock/      filter list parsing and request blocking
  ui/screens/        browser, camera, photo, settings
  ui/components/     translation overlay
```
