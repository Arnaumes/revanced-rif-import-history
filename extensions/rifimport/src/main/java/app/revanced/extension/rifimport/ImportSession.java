package app.revanced.extension.rifimport;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * The running, or finished but not yet acknowledged, import.
 * <p>
 * It lives outside {@link ImportHistoryActivity}, so an import keeps going when the screen is recreated (font size
 * or language change, or leaving with Home on a device that destroys background activities) and the new screen
 * shows its progress and result. All state is read and written on the main thread.
 */
final class ImportSession {
    interface Listener {
        void onProgress(int processed, int total);

        void onFinished(String message);
    }

    private static final String TAG = "rif import history";
    private static final String TEMP_PREFIX = "import-history";

    private static ImportSession current;

    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private Listener listener;
    private int processed;
    private int total;
    private String result;

    private ImportSession() {
    }

    /** The running import, or the finished one whose result wasn't acknowledged yet; null if none. */
    static ImportSession current() {
        return current;
    }

    /** Starts importing the history of the backup at {@code uri} on a background thread. */
    static ImportSession start(Context context, final Uri uri) {
        final Context app = context.getApplicationContext();
        final ImportSession session = new ImportSession();
        current = session;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String message = session.importHistory(app, uri);
                session.mainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        session.finish(message);
                    }
                });
            }
        }, "rif-import-history").start();
        return session;
    }

    /** Forgets the finished import, once its result was shown and acknowledged. A running import stays. */
    static void clearFinished() {
        if (current != null && current.result != null) current = null;
    }

    boolean isRunning() {
        return result == null;
    }

    /** Shows this import's state to {@code listener} now, and its updates from now on. */
    void attach(Listener listener) {
        this.listener = listener;
        if (result != null) {
            listener.onFinished(result);
        } else {
            listener.onProgress(processed, total);
        }
    }

    void detach(Listener listener) {
        if (this.listener == listener) this.listener = null;
    }

    private void progress(int processed, int total) {
        this.processed = processed;
        this.total = total;
        if (listener != null) listener.onProgress(processed, total);
    }

    private void finish(String message) {
        result = message;
        if (listener != null) listener.onFinished(message);
    }

    // region Background thread

    /** Returns the message to show. */
    private String importHistory(Context context, Uri uri) {
        deleteLeftovers(context.getCacheDir());
        File extracted = null;
        try {
            extracted = File.createTempFile(TEMP_PREFIX, ".db", context.getCacheDir());

            ExportPackage.Content content;
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new FileNotFoundException("Could not open " + uri);
            try {
                content = ExportPackage.extractHistoryDatabase(input, extracted);
            } finally {
                input.close();
            }

            if (content == ExportPackage.Content.NO_HISTORY) return ImportMessages.NO_HISTORY;
            if (content == ExportPackage.Content.UNKNOWN_FORMAT) return ImportMessages.NOT_A_BACKUP;

            SQLiteDatabase source;
            try {
                source = SQLiteDatabase.openDatabase(extracted.getPath(), null,
                        SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            } catch (SQLiteException e) {
                Log.w(TAG, "Not an SQLite database", e);
                return ImportMessages.NOT_A_BACKUP;
            }

            try {
                SQLiteDatabase target = HistoryDatabase.openWritable(context);
                HistoryImporter.Result result = HistoryImporter.merge(source, target, new HistoryImporter.ProgressListener() {
                    @Override
                    public void onProgress(final int processed, final int total) {
                        mainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                progress(processed, total);
                            }
                        });
                    }
                });
                Log.i(TAG, "Imported " + result.imported + " items, " + result.alreadyPresent + " already present, "
                        + result.duplicates + " duplicates in the backup");

                if (result.imported > 0) HistoryDatabase.notifyChanged(context);
                return ImportMessages.imported(result.imported, result.alreadyPresent, result.duplicates,
                        Locale.getDefault());
            } catch (HistoryImporter.NotAHistoryDatabaseException e) {
                Log.w(TAG, "No usable history in the database", e);
                return ImportMessages.NOT_A_BACKUP;
            } finally {
                source.close();
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Import failed", e);
            return ImportMessages.error(e);
        } finally {
            if (extracted != null) deleteDatabaseFiles(extracted);
        }
    }

    /** Temporary copies left behind by an import whose process was killed. */
    private static void deleteLeftovers(File cacheDir) {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(TEMP_PREFIX) && !file.delete()) Log.w(TAG, "Could not delete " + file);
        }
    }

    private static void deleteDatabaseFiles(File database) {
        for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) {
            File file = new File(database.getPath() + suffix);
            if (file.exists() && !file.delete()) Log.w(TAG, "Could not delete " + file);
        }
    }

    // endregion
}
