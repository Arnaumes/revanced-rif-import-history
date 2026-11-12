package app.revanced.extension.rifimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the merge against real Android SQLite databases with rif's history schema.
 */
@RunWith(AndroidJUnit4.class)
public class HistoryImporterTest {
    private static final HistoryImporter.ProgressListener IGNORE_PROGRESS = (processed, total) -> {
    };

    private final List<SQLiteDatabase> open = new ArrayList<>();
    private Context context;
    private File folder;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        folder = new File(context.getCacheDir(), "history-importer-test");
        deleteRecursively(folder);
        assertTrue(folder.mkdirs());
        context.deleteDatabase(ExportPackage.HISTORY_DATABASE);
    }

    @After
    public void tearDown() {
        for (SQLiteDatabase database : open) database.close();
        HistoryDatabase.closeFallback();
        deleteRecursively(folder);
        context.deleteDatabase(ExportPackage.HISTORY_DATABASE);
    }

    @Test
    public void importsOnlyNewItemsAndKeepsExistingOnes() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        insert(local, "https://reddit.com/r/a/comments/1/", "a", 100L, 5L, "1");
        insert(local, "https://imgur.com/local-only", "b", 200L, 2L, "2");

        SQLiteDatabase export = rifDatabase("export.db");
        insert(export, "https://reddit.com/r/a/comments/1/", "exported referer", 999L, 1L, "1");
        insert(export, "https://reddit.com/r/b/comments/3/", "b", 300L, 3L, "3");
        insert(export, "https://i.redd.it/4.jpg", null, 400L, 1L, "4");

        HistoryImporter.Result result = HistoryImporter.merge(export, local, IGNORE_PROGRESS);

        assertEquals(2, result.imported);
        assertEquals(1, result.alreadyPresent);
        assertEquals(0, result.duplicates);
        assertEquals(4, count(local));
        // Existing items are untouched, including the one the export also has.
        assertRow(local, "https://reddit.com/r/a/comments/1/", "a", 100L, 5L, "1");
        assertRow(local, "https://imgur.com/local-only", "b", 200L, 2L, "2");
        // New items keep all their exported values, NULLs included.
        assertRow(local, "https://reddit.com/r/b/comments/3/", "b", 300L, 3L, "3");
        assertRow(local, "https://i.redd.it/4.jpg", null, 400L, 1L, "4");
    }

    @Test
    public void importingTheSameBackupAgainAddsNothing() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase export = rifDatabase("export.db");
        for (int i = 0; i < 1200; i++) insert(export, "https://reddit.com/" + i, "r", 1000L + i, 1L, "t" + i);

        HistoryImporter.Result first = HistoryImporter.merge(export, local, IGNORE_PROGRESS);
        HistoryImporter.Result second = HistoryImporter.merge(export, local, IGNORE_PROGRESS);

        assertEquals(1200, first.imported);
        assertEquals(0, first.alreadyPresent);
        assertEquals(0, second.imported);
        assertEquals(1200, second.alreadyPresent);
        assertEquals(1200, count(local));
    }

    @Test
    public void duplicateUrisInTheBackupAreImportedOnceKeepingTheNewest() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase export = rifDatabase("export.db");
        insert(export, "https://reddit.com/dup", "old", 10L, 1L, "d");
        insert(export, "https://reddit.com/dup", "new", 20L, 7L, "d");

        HistoryImporter.Result result = HistoryImporter.merge(export, local, IGNORE_PROGRESS);

        assertEquals(1, result.imported);
        assertEquals(0, result.alreadyPresent);
        assertEquals(1, result.duplicates);
        assertEquals(1, count(local));
        assertRow(local, "https://reddit.com/dup", "new", 20L, 7L, "d");
    }

    @Test
    public void duplicatesOfAnExistingItemCountOnceAsPresent() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        insert(local, "https://reddit.com/known", "local", 1L, 4L, "k");
        SQLiteDatabase export = rifDatabase("export.db");
        insert(export, "https://reddit.com/known", "a", 10L, 1L, "k");
        insert(export, "https://reddit.com/known", "b", 20L, 1L, "k");
        insert(export, "https://reddit.com/new", "c", 30L, 1L, "n");

        HistoryImporter.Result result = HistoryImporter.merge(export, local, IGNORE_PROGRESS);

        assertEquals(1, result.imported);
        assertEquals(1, result.alreadyPresent);
        assertEquals(1, result.duplicates);
        assertRow(local, "https://reddit.com/known", "local", 1L, 4L, "k");
    }

    @Test
    public void skipsItemsWithoutUri() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase export = rifDatabase("export.db");
        insert(export, null, "r", 1L, 1L, "1");
        insert(export, "", "r", 2L, 1L, "2");
        export.execSQL("INSERT INTO uri_history (uri, accessed) VALUES (X'68747470', 4)"); // A BLOB, not text.
        insert(export, "https://reddit.com/ok", "r", 3L, 1L, "3");

        HistoryImporter.Result result = HistoryImporter.merge(export, local, IGNORE_PROGRESS);

        assertEquals(1, result.imported);
        assertEquals(0, result.alreadyPresent);
        assertEquals(1, count(local));
    }

    @Test
    public void fillsColumnsMissingFromTheBackupWithRifDefaults() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase export = database("old-export.db");
        export.execSQL("CREATE TABLE uri_history (_id INTEGER PRIMARY KEY, uri TEXT)");
        export.execSQL("INSERT INTO uri_history (uri) VALUES ('https://reddit.com/old')");

        long before = System.currentTimeMillis();
        HistoryImporter.Result result = HistoryImporter.merge(export, local, IGNORE_PROGRESS);

        assertEquals(1, result.imported);
        try (Cursor row = local.rawQuery("SELECT referer, accessed, access_count, thread_id FROM uri_history", null)) {
            assertTrue(row.moveToFirst());
            assertEquals("", row.getString(0));
            assertTrue(row.getLong(1) >= before);
            assertEquals(1L, row.getLong(2));
            assertEquals("", row.getString(3));
        }
    }

    @Test
    public void acceptsColumnNamesInAnyCase() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase export = database("upper.db");
        export.execSQL("CREATE TABLE uri_history (_id INTEGER PRIMARY KEY, URI TEXT, Referer TEXT, ACCESSED INTEGER, "
                + "Access_Count INTEGER, THREAD_ID TEXT)");
        export.execSQL("INSERT INTO uri_history (URI, Referer, ACCESSED, Access_Count, THREAD_ID) "
                + "VALUES ('https://reddit.com/upper', 'r', 5, 2, 'u')");

        assertEquals(1, HistoryImporter.merge(export, local, IGNORE_PROGRESS).imported);
        assertRow(local, "https://reddit.com/upper", "r", 5L, 2L, "u");
    }

    @Test
    public void rejectsDamagedDatabases() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase export = rifDatabase("damaged.db");
        for (int i = 0; i < 3000; i++) insert(export, "https://reddit.com/" + i, "referer " + i, i, 1L, "t" + i);
        export.close();
        open.remove(export);

        File file = new File(folder, "damaged.db");
        try (RandomAccessFile truncate = new RandomAccessFile(file, "rw")) {
            truncate.setLength(file.length() / 2);
        }

        SQLiteDatabase damaged;
        try {
            damaged = SQLiteDatabase.openDatabase(file.getPath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        } catch (SQLiteException e) {
            return; // Rejected when opening: the import screen shows "not a rif backup" for that too.
        }
        open.add(damaged);
        try {
            HistoryImporter.merge(damaged, local, IGNORE_PROGRESS);
            fail("Expected NotAHistoryDatabaseException");
        } catch (HistoryImporter.NotAHistoryDatabaseException expected) {
            // Expected.
        }
        assertEquals(0, count(local));
    }

    @Test
    public void rejectsDatabasesWithoutHistory() {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase subreddits = database("reddits.db");
        subreddits.execSQL("CREATE TABLE reddits (_id INTEGER PRIMARY KEY, name TEXT)");

        try {
            HistoryImporter.merge(subreddits, local, IGNORE_PROGRESS);
            fail("Expected NotAHistoryDatabaseException");
        } catch (HistoryImporter.NotAHistoryDatabaseException expected) {
            // Expected.
        }
    }

    @Test
    public void aFailedImportLeavesTheLocalHistoryUnchanged() {
        SQLiteDatabase local = rifDatabase("local.db");
        insert(local, "https://reddit.com/existing", "r", 1L, 1L, "e");
        local.execSQL("CREATE TRIGGER fail BEFORE INSERT ON uri_history WHEN NEW.uri = 'https://reddit.com/boom' "
                + "BEGIN SELECT RAISE(ABORT, 'boom'); END");

        SQLiteDatabase export = rifDatabase("export.db");
        insert(export, "https://reddit.com/first", "r", 30L, 1L, "1");
        insert(export, "https://reddit.com/second", "r", 20L, 1L, "2");
        insert(export, "https://reddit.com/boom", "r", 10L, 1L, "3");

        try {
            HistoryImporter.merge(export, local, IGNORE_PROGRESS);
            fail("Expected the trigger to abort the import");
        } catch (SQLiteException expected) {
            // Expected.
        } catch (HistoryImporter.NotAHistoryDatabaseException e) {
            throw new AssertionError(e);
        }

        assertEquals(1, count(local));
        assertRow(local, "https://reddit.com/existing", "r", 1L, 1L, "e");
    }

    @Test
    public void reportsProgressUpToTheTotal() throws Exception {
        SQLiteDatabase local = rifDatabase("local.db");
        SQLiteDatabase export = rifDatabase("export.db");
        for (int i = 0; i < 1234; i++) insert(export, "https://reddit.com/" + i, "r", i, 1L, "t");

        final List<int[]> updates = new ArrayList<>();
        HistoryImporter.merge(export, local, (processed, total) -> updates.add(new int[]{processed, total}));

        assertEquals(0, updates.get(0)[0]);
        int[] last = updates.get(updates.size() - 1);
        assertEquals(1234, last[0]);
        assertEquals(1234, last[1]);
    }

    @Test
    public void createsRifSchemaWhenTheAppHasNoHistoryDatabaseYet() throws Exception {
        // No rif history provider in the test app, so this exercises the fallback helper.
        SQLiteDatabase target = HistoryDatabase.openWritable(context);
        assertEquals(2, target.getVersion());
        assertEquals(1, DatabaseUtils.longForQuery(target,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'uri_history_uri_idx'", null));

        SQLiteDatabase export = rifDatabase("export.db");
        insert(export, "https://reddit.com/x", "r", 5L, 2L, "x");
        assertEquals(1, HistoryImporter.merge(export, target, IGNORE_PROGRESS).imported);
        assertRow(target, "https://reddit.com/x", "r", 5L, 2L, "x");
    }

    // region Helpers

    private SQLiteDatabase database(String name) {
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(new File(folder, name), null);
        open.add(database);
        return database;
    }

    /** rif's UriHistoryProvider schema. */
    private SQLiteDatabase rifDatabase(String name) {
        SQLiteDatabase database = database(name);
        database.execSQL("CREATE TABLE uri_history (_id INTEGER PRIMARY KEY, uri TEXT, referer TEXT, accessed INTEGER, "
                + "access_count INTEGER, thread_id TEXT);");
        database.execSQL("CREATE INDEX uri_history_uri_idx ON uri_history(uri);");
        database.execSQL("CREATE INDEX uri_history_thread_id_idx ON uri_history(thread_id);");
        return database;
    }

    private static void insert(SQLiteDatabase database, String uri, String referer, long accessed, long accessCount,
                               String threadId) {
        database.execSQL("INSERT INTO uri_history (uri, referer, accessed, access_count, thread_id) VALUES (?, ?, ?, ?, ?)",
                new Object[]{uri, referer, accessed, accessCount, threadId});
    }

    private static long count(SQLiteDatabase database) {
        return DatabaseUtils.longForQuery(database, "SELECT COUNT(*) FROM uri_history", null);
    }

    private static void assertRow(SQLiteDatabase database, String uri, String referer, long accessed, long accessCount,
                                  String threadId) {
        try (Cursor row = database.rawQuery(
                "SELECT referer, accessed, access_count, thread_id FROM uri_history WHERE uri = ?", new String[]{uri})) {
            assertTrue("missing " + uri, row.moveToFirst());
            if (referer == null) {
                assertNull(row.getString(0));
            } else {
                assertEquals(referer, row.getString(0));
            }
            assertEquals(accessed, row.getLong(1));
            assertEquals(accessCount, row.getLong(2));
            assertEquals(threadId, row.getString(3));
            assertFalse("duplicate " + uri, row.moveToNext());
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // endregion
}
