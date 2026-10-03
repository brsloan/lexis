# Lexis — a StarDict reader for dense dictionaries

Lexis is an app for Android and for desktop computers (Windows, macOS and Linux) for reading
StarDict dictionaries you supply yourself. It is
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

## Desktop app (Windows, macOS, Linux)

The desktop app shares the dictionary engine and the article renderer with the Android app, laid
out for a big screen:

* **Search sidebar** on the left: type to get suggestions, use ↑/↓ and Enter to pick one; when the
  box is empty it lists your lookup history (hover a row to remove it).
* **Reader** on the right with back/forward (Alt+←/→, ⌘[/⌘] on a Mac, or the mouse's back and
  forward buttons), find in entry (Ctrl+F), and a side panel with the **senses outline** and
  **nearby words** (Ctrl+O toggles it).
* Clicking a cross-reference opens it; clicking an abbreviation shows its expansion.
* **Look up the clipboard** (Ctrl+Shift+V), text size (Ctrl+= / Ctrl+- / Ctrl+0), themes and the
  other settings from the Android app; *Export word list…* saves an HTML or text file.
* `Lexis <word>` on the command line opens straight to that word.

Dictionaries are **indexed where they are**: *File → Dictionaries… → Add folder…* scans a folder
(and its sub-folders) for `.ifo` + `.idx` + `.dict.dz` sets and builds a search index, without
copying the files. Keep them in place afterwards; a dictionary whose files have moved is marked
as missing. The index, history and settings live in the per-user data folder:
`%APPDATA%\Lexis` on Windows, `~/Library/Application Support/Lexis` on macOS and
`~/.local/share/lexis` on Linux (override with `-Dlexis.home=...`).

### Building the desktop app

Desktop builds need a full JDK 17+ that includes `jpackage` (Temurin, Zulu, or JetBrains
Runtime; Android Studio's bundled `jbr` lacks `jpackage`, so use it only for running, not
packaging).

```bash
./gradlew :desktop:run
```

runs it from source. Installers are made with

```bash
./gradlew :desktop:packageDistributionForCurrentOS
```

which writes `.msi` and `.exe` installers on Windows, a `.dmg` on macOS, and `.deb` + `.rpm`
packages on Linux to `desktop/build/compose/binaries/main/`. Each bundles its own trimmed Java
runtime, so users need nothing else installed. `jpackage` can only package for the OS it runs
on; the **Desktop builds** GitHub Actions workflow (`.github/workflows/desktop.yml`) builds all
three. Run it from the Actions tab, or push a `v*` tag to attach the installers to a release.
The macOS build is not notarized, so the first launch needs right-click → *Open*.

```bash
./gradlew :desktop:crossPlatformJar
```

builds `desktop/build/dist/lexis-desktop-<version>-all.jar`, a single jar that runs on any of the
three systems (Intel or ARM) where Java 17+ is installed: `java -jar lexis-desktop-1.0.0-all.jar`.

## Building the Android app

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
is the plain Kotlin `core` module, with JUnit tests under `core/src/test`:

```bash
./gradlew :core:test
```

`RealDictionaryTest` additionally exercises real StarDict files when the environment
variable `DICT_DIR` points at a folder containing them (it is skipped otherwise).

The fixtures under `core/src/test/resources` are synthetic: invented headwords and
invented quotations, written to reproduce the markup conventions this parser handles
(homographs, nested senses, citation runs, bracketed etymologies). No dictionary content
is bundled with this project.

`desktop/src/test/.../Screenshots.kt` is a visual smoke test for the desktop UI: with
`DICT_DIR` and `LEXIS_SCREENSHOTS` (an output folder) set, `./gradlew :desktop:test` indexes the
dictionaries into a scratch data folder and renders the main window offscreen to PNG files.

## Installing dictionaries on the phone

1. Copy the dictionary folder(s) to the phone (e.g. `Download/my-dictionary` containing the
   `.ifo`, `.idx` and `.dict.dz` files). Multi-part dictionaries can sit in one parent folder.
2. In Lexis choose *Dictionaries → Add folder* and pick the parent folder (sub-folders are
   scanned), or *Add files* and multi-select the three files of one dictionary.
3. The files are copied into the app's private storage and indexed once (a very large
   dictionary takes roughly a minute). The originals can then be deleted.

## Project layout

```
core/src/main/kotlin/com/lexis/reader/core/   pure-Kotlin engine shared by both apps
    DictReaders.kt     dictzip (.dict.dz) chunked random-access reader, plain .dict reader
    StarDict.kt        .ifo / .idx / .syn parsing, typed entry segments
    Xdxf.kt            inline markup tokenizer -> styled runs
    EntryParser.kt     entry line structure -> blocks (senses, quotations, etymology, ...)
    Article.kt         block model, outline, snippets
    Exporter.kt        HTML / plain-text export
    TextNormalizer.kt  search normalisation
app/src/main/java/com/lexis/reader/            Android app
  data/        SQLite (dictionaries, words index, history), import, settings, export/share
  ui/          Jetpack Compose screens: Home, Entry (reader), Dictionaries, Settings, Export
desktop/src/main/kotlin/com/lexis/desktop/     desktop app (Compose Multiplatform)
  Main.kt      window, saved window bounds, command-line word
  data/        SQLite via JDBC (same schema), in-place indexing, settings file, export
  ui/          sidebar, reader pane, menu bar, dialogs; ArticleView/RichText mirror the Android ones
```

The article renderer (`ArticleView.kt`, `RichText.kt`, the colour palettes) exists in both
`app` and `desktop`, since Jetpack Compose and Compose Multiplatform builds can't share one source
folder without converting the project to Kotlin Multiplatform. Keep the two copies in step when
changing how entries look.

## License

MIT — see [LICENSE](LICENSE). The licence covers this software only; dictionaries you
supply remain subject to their own terms.
