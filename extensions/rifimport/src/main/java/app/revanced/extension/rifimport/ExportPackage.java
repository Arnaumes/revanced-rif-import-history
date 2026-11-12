package app.revanced.extension.rifimport;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Reads the packages rif's "Export settings and history" creates: a .zip with settings.xml, usage_stats.xml,
 * version.xml, reddits.db, ... and browserhistory.db, the history. A bare browserhistory.db is accepted too.
 */
public final class ExportPackage {
    /** rif's history database (UriHistoryProvider). */
    public static final String HISTORY_DATABASE = "browserhistory.db";

    /** Far above any real history (about 150 bytes per item); guards against filling the storage. */
    static final long MAX_DATABASE_BYTES = 512L * 1024L * 1024L;

    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] EMPTY_ZIP_MAGIC = {'P', 'K', 5, 6};
    private static final byte[] SQLITE_MAGIC = "SQLite format 3\0".getBytes(Charset.forName("US-ASCII"));

    public enum Content {
        /** The history database was copied to the destination. */
        HISTORY,
        /** A zip without history, e.g. one made with "Export settings" instead of "Export settings and history". */
        NO_HISTORY,
        /** Neither a zip nor an SQLite database, or a zip whose browserhistory.db isn't an SQLite database. */
        UNKNOWN_FORMAT,
    }

    private ExportPackage() {
    }

    /**
     * Copies the history database of the package in {@code input} to {@code destination}.
     * Zip entries are never written anywhere else, whatever their names are.
     *
     * @throws IOException if the package can't be read, e.g. a corrupted zip.
     */
    public static Content extractHistoryDatabase(InputStream input, File destination) throws IOException {
        BufferedInputStream in = new BufferedInputStream(input);
        in.mark(SQLITE_MAGIC.length);
        byte[] header = readUpTo(in, SQLITE_MAGIC.length);
        in.reset();

        if (startsWith(header, SQLITE_MAGIC)) {
            copy(in, destination);
            return Content.HISTORY;
        }

        if (startsWith(header, EMPTY_ZIP_MAGIC)) {
            return Content.NO_HISTORY;
        }

        if (!startsWith(header, ZIP_MAGIC)) {
            return Content.UNKNOWN_FORMAT;
        }

        try {
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && isHistoryDatabase(entry.getName())) {
                    copy(zip, destination);
                    return isSqliteDatabase(destination) ? Content.HISTORY : Content.UNKNOWN_FORMAT;
                }
            }
        } catch (ZipException | IllegalArgumentException e) {
            // IllegalArgumentException: entry names that aren't valid UTF-8.
            throw new IOException("The zip file is damaged: " + e.getMessage(), e);
        }

        return Content.NO_HISTORY;
    }

    private static boolean isSqliteDatabase(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            return startsWith(readUpTo(in, SQLITE_MAGIC.length), SQLITE_MAGIC);
        } finally {
            in.close();
        }
    }

    /** True for browserhistory.db at any folder depth, so a re-zipped export still works. */
    static boolean isHistoryDatabase(String entryName) {
        String name = entryName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return HISTORY_DATABASE.equalsIgnoreCase(name);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static byte[] readUpTo(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, total, length - total);
            if (read < 0) break;
            total += read;
        }
        if (total == length) return buffer;
        byte[] shorter = new byte[total];
        System.arraycopy(buffer, 0, shorter, 0, total);
        return shorter;
    }

    private static void copy(InputStream in, File destination) throws IOException {
        OutputStream out = new FileOutputStream(destination);
        try {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > MAX_DATABASE_BYTES) {
                    throw new IOException("The history database is too large");
                }
                out.write(buffer, 0, read);
            }
        } finally {
            out.close();
        }
    }
}
