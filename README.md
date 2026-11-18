# revanced-rif-import-history

[![Build](https://github.com/Arnaumes/revanced-rif-import-history/actions/workflows/build.yml/badge.svg)](https://github.com/Arnaumes/revanced-rif-import-history/actions/workflows/build.yml)

> [!IMPORTANT]
> **This patch is now part of [MojiRS/revanced-rif-patches](https://github.com/MojiRS/revanced-rif-patches).** This
> repository is archived and won't get updates.
>
> Since MojiRS's version 1.9.0, its **Fix history import** patch makes rif's **Import settings** (renamed **Import
> settings and history**) add the backup's history too, the same way as this patch: only missing items are added,
> and nothing is removed. The difference is that it runs as part of **Import settings**, which also replaces all
> your settings with the ones in the backup.
>
> To switch: in ReVanced Manager, remove this bundle, update MojiRS's bundle to 1.9.0 or newer
> (`https://raw.githubusercontent.com/MojiRS/revanced-rif-patches/main/bundles/stable.json`), select **Fix history
> import** and patch again. You don't need both.

A [ReVanced](https://revanced.app) patch for **rif is fun for Reddit 5.6.22**, the last release of the discontinued
Reddit client. It works with the free app (`com.andrewshu.android.reddit`) and with **rif is fun golden platinum**
(`com.andrewshu.android.redditdonation`).

rif's last update added **Settings → Backup → Export settings AND HISTORY**, which saves your link history in a
`.zip`. But rif can't bring that history back: its **Import settings** ignores the history in the backup (and
overwrites all your settings). This patch adds the missing **Import history**.

## Patch

| Patch | What it does |
|-------|--------------|
| **Import history** | Adds **Import history** to **Settings → Backup**, below "Import settings". Pick a backup `.zip` and its history is added to rif's history. |

How the import works:

- **Only new items are added.** A history item is identified by its link, the same way rif identifies it when it
  records a visit. Items you already have are skipped, so importing the same backup twice adds nothing the second
  time. You can import several backups one after the other to combine them.
- **Nothing is removed or changed.** Your existing history stays exactly as it is, including the last visit time
  and visit count of items that are also in the backup.
- **Imported items keep their details:** where you opened them from, when you last opened them, how often, and the
  post they belong to. Posts in the imported history show as read, like any other read post.
- **Safe to interrupt.** The import runs in a database transaction. If it fails or is interrupted, importing the
  same backup again adds whatever is still missing.
- **Only the history.** Settings, subreddits, multireddits and filters are not touched; rif's own **Import
  settings** restores those (and overwrites them).

Accepted files: the `.zip` made by **Export settings AND HISTORY** (also when re-zipped into a folder), or a bare
`browserhistory.db`. A backup made with plain **Export settings** has no history; the import screen says so.

The import screen looks and behaves like rif's own export/import screen, in your current rif theme.

## Installation

You need your own copy of the original **rif is fun 5.6.22** APK (free or golden platinum). This project doesn't
provide APKs.

### ReVanced Manager

1. In ReVanced Manager, go to **Patches**, tap the pencil button, then **Add patches → Enter URL**, and paste:

   ```
   https://raw.githubusercontent.com/Arnaumes/revanced-rif-import-history/main/bundles/stable.json
   ```

   It always points at the newest release, so Manager keeps the patch up to date. (Or download `patches.rvp` from
   [Releases](https://github.com/Arnaumes/revanced-rif-import-history/releases) and add it as a local file.)
2. Patch your **original, unpatched** rif 5.6.22 APK. Select **Import history** together with the patches you
   already use: patching rebuilds the whole app, so anything you leave out is gone. For rif that's usually the
   official **Spoof client** patch (needed to log in),
   [MojiRS/revanced-rif-patches](https://github.com/MojiRS/revanced-rif-patches) and
   [notBrian2/revanced-rif-fixes](https://github.com/notBrian2/revanced-rif-fixes). This bundle changes nothing
   they change.
3. Install. If your current rif was patched by the same Manager, it updates in place and keeps your data.
   Otherwise Android makes you uninstall rif first, which deletes its data: back up with **Export settings AND
   HISTORY** before, then restore with **Import settings** and **Import history** after.

### ReVanced CLI

```bash
java -jar revanced-cli-6.0.0-all.jar patch -p patches.rvp -b -o rif-patched.apk rif-5.6.22.apk
```

ReVanced CLI 6 wants a signature and build attestation for each bundle, or `-b` to skip that check; this bundle
(like the other rif bundles) publishes neither. To apply other bundles at the same time, add `-p <file> -b` for each.

## Importing your history

1. In rif, open **Settings → Backup → Import history**.
2. Pick your backup, e.g. `rif_is_fun_golden_platinum_settings_and_history_<date>.zip`.
3. Wait for the result, e.g. "Imported 12,345 new history items. 3 items were already in your history and stayed
   unchanged.", then tap **OK**.

## Building

Requires JDK 17 or newer (tested with 21) and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in
`local.properties`).

```bash
./gradlew build
# output: patches/build/libs/patches-<version>.rvp
```

`./gradlew buildAndroid` builds only the `.rvp`. Like the official template's `buildAndroid`, the `.rvp` contains
the patch classes (for ReVanced CLI), their `classes.dex` (for ReVanced Manager) and the extension
`extensions/rifimport.rve`.

No GitHub token is needed. The [official patches template](https://github.com/ReVanced/revanced-patches-template)
(which the other rif bundles use) gets the ReVanced Gradle plugin and ReVanced Patcher from GitHub Packages, which
requires a token even for public packages. This project doesn't use that plugin:

- the patcher API (ReVanced Patcher 22, the version the other rif bundles use) is extracted at build time from the
  public [ReVanced CLI 6.0.0](https://github.com/ReVanced/revanced-cli/releases/tag/v6.0.0) release jar, after
  checking its SHA-256. It's only used to compile: ReVanced CLI and Manager provide the patcher at runtime;
- the extension is built and dexed by the Android Gradle plugin, and the patches are dexed with D8, like the
  official plugin does.

Everything else comes from Maven Central, Google's Maven repository and the Gradle plugin portal.

Testing: see [docs/testing.md](docs/testing.md) for the automated tests and how to test the patched app end to end.

### Releasing

1. Set `version` in `gradle.properties` and add a section for it to `CHANGELOG.md` (it becomes the release notes
   that ReVanced Manager shows).
2. Commit, then tag and push: `git tag v1.0.1 && git push origin main v1.0.1`.

The [release workflow](.github/workflows/release.yml) builds and tests, publishes the release with `patches.rvp`,
and updates `bundles/*.json` (the links ReVanced Manager follows). Tags with a suffix like `v1.1.0-rc.1` become
pre-releases, which only `bundles/latest.json` and `bundles/dev.json` point to.

## How it works

```
patches/                                  the patch (Kotlin, ReVanced Patcher API)
  src/main/kotlin/app/revanced/patches/rifimport/
    history/ImportHistoryPatch.kt         adds the settings entry and the screen; adapts the screen to rif
    shared/Rif.kt                         package names, version, rif class names
  src/e2e/kotlin/                         testing-only patch, built with -Pe2e (see docs/testing.md)
extensions/rifimport/                     code merged into rif (Java, Android SDK only, minSdk 21)
  src/main/java/app/revanced/extension/rifimport/
    ImportHistoryActivity.java            the import screen: file picker, progress, result
    ImportSession.java                    runs the import; survives the screen being recreated
    ExportPackage.java                    finds browserhistory.db in a backup .zip
    HistoryImporter.java                  merges the backup's history into rif's history
    HistoryDatabase.java                  opens rif's history database through rif's own history provider
    RifTheme.java                         rif's current theme (body written by the patch)
    ImportMessages.java                   texts of the screen
docs/export-format.md                     what's in rif's backups
```

- **Resources**: the patch registers `ImportHistoryActivity` in the manifest and adds the **Import history** entry
  to `res/xml/backup_preferences.xml`. Its intent targets `com.andrewshu.android.reddit` like rif's own entries;
  rif's settings screens retarget those intents to the installed package (golden platinum or renamed) at runtime.
- **Bytecode**: rif is obfuscated, and the free and golden platinum builds use different names. So the patch only
  relies on unobfuscated names (`SettingsExportImportActivity` and its `onCreate`) and reads everything else from
  the APK:
  - the import screen's superclass becomes the superclass of rif's `SettingsExportImportActivity` (rif's
    `BaseActivity`), including the superclass constructor call;
  - `RifTheme.activityTheme()` gets the theme lookup that `SettingsExportImportActivity.onCreate` does before
    `setTheme`.

  If rif's code doesn't look as expected, the patch fails with a message instead of producing a broken app.
- **Database**: the import writes through rif's own `SQLiteOpenHelper`, taken from its `UriHistoryProvider`
  (found by type, because of the obfuscation), so it shares rif's connection instead of competing with it for the
  database lock, and it steps aside whenever rif needs the database. The backup is read through a separate
  connection and never attached to rif's, so a damaged backup can't affect rif's database. Afterwards the import
  notifies rif's history observers, like rif's provider does.

## Credits

- [MojiRS/revanced-rif-patches](https://github.com/MojiRS/revanced-rif-patches) and
  [notBrian2/revanced-rif-fixes](https://github.com/notBrian2/revanced-rif-fixes), which keep rif usable and showed
  how to patch it.
- [ReVanced](https://github.com/ReVanced) for the patcher, the CLI and Manager.
- talklittle, for rif is fun.

This project is not affiliated with rif is fun, talklittle, Reddit or ReVanced.

## License

[GNU General Public License v3.0](LICENSE), like ReVanced Patcher and the other rif patch bundles.
