# Lexis — a StarDict reader for dense dictionaries

Lexis is an Android app for reading StarDict dictionaries you supply yourself. It is
designed to work well with the most complex dictionaries: large historical dictionaries
whose entries pack dozens of senses and thousands of quotations into a single article.
Any StarDict dictionary (`x`, `h`, `m` types, `.dict` or `.dict.dz`, optional `.syn`) can
be added; the reader gets the most out of richly structured XDXF markup.

## What it does

* **Home screen = search box + lookup history.** Type to get prefix suggestions across all
  dictionaries (diacritics, ligatures like *æ*, and case are ignored), press search to open
  the best match. Every word you open is recorded with a one-line definition snippet,
  newest first, like GoldenDict's history.
* **Reader built for long, complex articles.**
  * Homographs (`▪ I.` *noun*, `▪ II.` *verb*) become labelled sections.
  * Sense numbers (`I.`, `1.`, `a.`, `(a)`) get hanging indents and nesting, with
    obsolete/alien markers (`†`, `‖`) highlighted.
  * Etymologies are dimmed and truncated to four lines; tap to expand.
  * **Quotations are split into one citation per line** (bold date, source in grey, quoted
    text in the dictionary's own colour) instead of the run-together paragraph other readers show.
    Each block is collapsed by default to "▸ 12 quotations, 1375–1876" (first attestation
    date at a glance) and expands with a tap. Settings offer collapsed / first-only / all,
    plus expand-all / collapse-all per entry.
  * Cross-references (`<kref>`) are links; tapping opens that word.
  * **Abbreviations are tappable.** Dictionaries that ship their own glossary
    entries (Obs., OFr., pa. pple., Shakes., Yorks., …) are supported: tapping any abbreviation shows the
    full expansion. A setting can also expand unambiguous abbreviations inline, either in
    definitions and etymologies only or everywhere including quotation sources. Multi-valued
    ones such as "a." (adjective / adopted from / ante) are never substituted blindly.
  * **Senses sheet**: an outline of homographs, branches and numbered senses to jump
    around 900 KB articles like *set* or *run*.
  * **Find in entry** with match counter and next/previous.
  * Nearby words (browse the index around the current headword), text-size controls,
    serif/sans toggle, light/dark/system theme, text selection for copying.
* **Export history for review.** *Export word list…* builds an HTML (or plain-text)
  document with every looked-up word and its full entry (quotations optional), then opens
  the share sheet so you can email it to yourself. "Only words since last export" makes
  weekly review emails easy; words newer than the last export are shown bold on the home
  screen.
* **Look up from other apps**: select text anywhere and choose *Lexis* from the selection
  toolbar, or share text to Lexis.

## Building

Requirements: an Android SDK with compileSdk 35 and a JDK 17 or newer (Android
Studio's bundled JBR works; note that a JRE is not enough). The project uses
Gradle 9.6 / AGP 9.4.0 / Kotlin 2.2.10 / Compose BOM 2024.12.

1. Open the `lexis` folder in Android Studio and let it sync (it will download the
   dependencies and, if needed, offer to install the SDK platform).
2. Run the `app` configuration on a device or emulator (minSdk 26).

Command line (once an SDK is installed and `ANDROID_HOME` or `local.properties` points at it):

```bash
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

### Release builds

Release builds are minified (R8) and shrunk, and must be signed to be installable.
Signing is driven by a `keystore.properties` file in this folder, which is **not** in
version control:

```properties
storeFile=C:/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Create the keystore once with:

```bash
keytool -genkeypair -v -keystore your-release.jks -alias <alias> -keyalg RSA -keysize 2048 -validity 10000
```

Then `./gradlew :app:assembleRelease` produces a signed `app-release.apk` in
`app/build/outputs/apk/release/`. Without `keystore.properties` the same command still
builds, but emits an unsigned APK that Android will refuse to install. Keep the keystore
backed up: Android only allows an app to be upgraded in place by a build signed with the
same key.

### Engine tests

The dictionary engine (dictzip random access, `.idx`/`.syn` parsing, XDXF parser, exporter)
is plain Kotlin with JUnit tests under `app/src/test`. They run as ordinary unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

`RealDictionaryTest` additionally exercises real StarDict files when the environment
variable `DICT_DIR` points at a folder containing them (it is skipped otherwise).

The fixtures under `app/src/test/resources` are synthetic: invented headwords and
invented quotations, written to reproduce the markup conventions this parser handles
(homographs, nested senses, citation runs, bracketed etymologies). No dictionary content
is bundled with this project.

## Installing dictionaries on the phone

1. Copy the dictionary folder(s) to the phone (e.g. `Download/my-dictionary` containing the
   `.ifo`, `.idx` and `.dict.dz` files). Multi-part dictionaries can sit in one parent folder.
2. In Lexis choose *Dictionaries → Add folder* and pick the parent folder (sub-folders are
   scanned), or *Add files* and multi-select the three files of one dictionary.
3. The files are copied into the app's private storage and indexed once (a very large
   dictionary takes roughly a minute). The originals can then be deleted.

## Project layout

```
app/src/main/java/com/lexis/reader/
  core/        pure-Kotlin engine (no Android imports)
    DictReaders.kt     dictzip (.dict.dz) chunked random-access reader, plain .dict reader
    StarDict.kt        .ifo / .idx / .syn parsing, typed entry segments
    Xdxf.kt            inline markup tokenizer -> styled runs
    EntryParser.kt     entry line structure -> blocks (senses, quotations, etymology, ...)
    Article.kt         block model, outline, snippets
    Exporter.kt        HTML / plain-text export
    TextNormalizer.kt  search normalisation
  data/        SQLite (dictionaries, words index, history), import, settings, export/share
  ui/          Jetpack Compose screens: Home, Entry (reader), Dictionaries, Settings, Export
```

## License

MIT — see [LICENSE](LICENSE). The licence covers this software only; dictionaries you
supply remain subject to their own terms.
