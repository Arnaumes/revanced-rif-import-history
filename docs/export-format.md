# rif's "settings and history" backup

rif is fun 5.6.22 creates it with **Settings → Backup → Export settings AND HISTORY**, as
`<app name>_settings_and_history_<yyyy-MM-dd>.zip`, e.g. `rif_is_fun_settings_and_history_<date>.zip` or
`rif_is_fun_golden_platinum_settings_and_history_<date>.zip`.
Plain **Export settings** creates the same zip without `browserhistory.db`.

## Contents

| Entry | What it is | Restored by rif's "Import settings" |
|-------|------------|-------------------------------------|
| `settings.xml`, `usage_stats.xml`, `version.xml` | Shared preferences | Yes (overwritten) |
| `threadfilters.db`, `reddits.db`, `multireddits.db`, `externalbrowserurls.db`, `subredditnewpostsubscriptions.db` | Filters, subreddits, multireddits, browser rules, new post notifications | Yes (overwritten) |
| `browserhistory.db` | Link visit history | **No**, hence the "Import history" patch |

(`SettingsExportImportActivity` and the tasks in `com.andrewshu.android.reddit.settings.export`; the database list is
`o4.a` in golden platinum.)

## browserhistory.db

The database of rif's `UriHistoryProvider` (`content://<package>.history.urihistoryprovider/urihistoryitems`),
version 2 (`PRAGMA user_version`), rollback journal (rif turns off write-ahead logging on Android 9+):

```sql
CREATE TABLE uri_history (
    _id INTEGER PRIMARY KEY,
    uri TEXT,           -- the visited link: a post, an image, a video, ...
    referer TEXT,       -- where it was opened from: a subreddit name or an API URL (can be NULL)
    accessed INTEGER,   -- last visit, milliseconds since 1970
    access_count INTEGER,
    thread_id TEXT      -- the post's id36, e.g. "abc123"
);
CREATE INDEX uri_history_uri_idx ON uri_history(uri);
CREATE INDEX uri_history_thread_id_idx ON uri_history(thread_id);
```

rif records a visit by URI: a known URI gets its `access_count` incremented and `accessed` updated, a new one is
inserted with `access_count = 1`. A post shows as read when its URI or its `thread_id` is in the table.

The "Import history" patch uses the same identity: an item of the backup is imported if its `uri` isn't in the
local table yet, with all its columns as they are in the backup. Local items are never changed or deleted.
