# Testing

## Automated tests

```bash
./gradlew build                                           # unit tests (reading backups, messages), lint, bundle
./gradlew :extensions:rifimport:connectedDebugAndroidTest # merge tests on a device or emulator (real SQLite)
```

The instrumented tests run the merge against real Android SQLite databases with rif's schema: only new items are
added, existing ones are untouched, duplicates, missing columns, damaged databases, and a failure leaving the local
history unchanged.

## End to end

What the automated tests can't cover: the patch applied to the real APK, rif's settings screen, the file picker,
rif's theme and base activity, and the import through rif's own history provider.

You need an original rif is fun 5.6.22 APK, the [ReVanced CLI 6.0.0](https://github.com/ReVanced/revanced-cli/releases/tag/v6.0.0)
jar, and an emulator or a test device. Don't use the debuggable build below for the rif you actually use.

1. Build the bundle with the testing patches, then patch with **Debuggable (testing only)** enabled, so
   `adb shell run-as` can read and write rif's databases:

   ```bash
   ./gradlew buildAndroid -Pe2e
   java -jar revanced-cli-6.0.0-all.jar patch -p patches/build/libs/patches-1.0.0.rvp -b \
     -e "Debuggable (testing only)" -o rif-test.apk rif-5.6.22.apk
   ```

   Build again without `-Pe2e` afterwards: a release must never contain the testing patches.

2. Start an emulator without touching its saved data. Play Store images also need `-skip-adb-auth`:

   ```bash
   emulator -avd <name> -read-only -no-snapshot -skip-adb-auth
   adb install rif-test.apk
   ```

3. Open rif once, then stop it and give it some existing history to merge with, for example a database with a few
   items that are also in the backup (with different values) and a few that aren't:

   ```bash
   adb shell am force-stop com.andrewshu.android.redditdonation
   adb push seed.db /data/local/tmp/seed.db
   adb shell "cat /data/local/tmp/seed.db | run-as com.andrewshu.android.redditdonation sh -c 'cat > databases/browserhistory.db'"
   ```

   `seed.db` needs rif's schema (see [export-format.md](export-format.md)) and `PRAGMA user_version = 2`, or rif
   tries to create the table again. (Use `com.andrewshu.android.reddit` for the free app.)

4. Copy a backup to the device (`adb push backup.zip /sdcard/Download/`), then in rif: **Settings → Backup →
   Import history**, pick it, and check the counts on the result screen.

5. Pull the database and compare it with the seed and the backup:

   ```bash
   adb exec-out run-as com.andrewshu.android.redditdonation cat databases/browserhistory.db > after.db
   sqlite3 after.db "ATTACH 'seed.db' AS seed; ATTACH 'backup-browserhistory.db' AS backup; ..."
   ```

   Expected: every seed row unchanged, every backup URI present once, imported rows identical to the backup, and
   `PRAGMA integrity_check` ok. rif's **Settings → History → Clear browsing history** dialog shows the total too
   (tap No).

Also worth checking: importing the same backup twice (the second time adds nothing), a fresh install without any
history, a backup made with plain "Export settings" and a file that isn't a backup (both get a message), rif's
dark theme, and leaving with Home during a long import with "Don't keep activities" on (the screen comes back
with the import's progress or result).
