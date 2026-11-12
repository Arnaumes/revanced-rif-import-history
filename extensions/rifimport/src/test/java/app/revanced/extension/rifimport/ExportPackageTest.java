package app.revanced.extension.rifimport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportPackageTest {
    private static final byte[] DATABASE = bytes("SQLite format 3\0 pretend database contents");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void extractsHistoryFromRifExport() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("settings.xml", bytes("<map />"));
        entries.put("usage_stats.xml", bytes("<map />"));
        entries.put("version.xml", bytes("<map />"));
        entries.put("reddits.db", bytes("SQLite format 3\0 subreddits"));
        entries.put("browserhistory.db", DATABASE);

        File destination = folder.newFile();
        assertEquals(ExportPackage.Content.HISTORY, ExportPackage.extractHistoryDatabase(zip(entries), destination));
        assertArrayEquals(DATABASE, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void findsHistoryInSubfolderOfRezippedExport() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("backup/", new byte[0]);
        entries.put("backup/settings.xml", bytes("<map />"));
        entries.put("backup/BrowserHistory.db", DATABASE);

        File destination = folder.newFile();
        assertEquals(ExportPackage.Content.HISTORY, ExportPackage.extractHistoryDatabase(zip(entries), destination));
        assertArrayEquals(DATABASE, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void settingsOnlyExportHasNoHistory() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("settings.xml", bytes("<map />"));
        entries.put("reddits.db", bytes("SQLite format 3\0 subreddits"));

        assertEquals(ExportPackage.Content.NO_HISTORY, ExportPackage.extractHistoryDatabase(zip(entries), folder.newFile()));
    }

    @Test
    public void emptyZipHasNoHistory() throws IOException {
        assertEquals(ExportPackage.Content.NO_HISTORY,
                ExportPackage.extractHistoryDatabase(zip(new LinkedHashMap<String, byte[]>()), folder.newFile()));
    }

    @Test
    public void acceptsBareHistoryDatabase() throws IOException {
        File destination = folder.newFile();
        assertEquals(ExportPackage.Content.HISTORY,
                ExportPackage.extractHistoryDatabase(new ByteArrayInputStream(DATABASE), destination));
        assertArrayEquals(DATABASE, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void rejectsOtherFiles() throws IOException {
        assertEquals(ExportPackage.Content.UNKNOWN_FORMAT,
                ExportPackage.extractHistoryDatabase(new ByteArrayInputStream(bytes("%PDF-1.7 not a backup")), folder.newFile()));
        assertEquals(ExportPackage.Content.UNKNOWN_FORMAT,
                ExportPackage.extractHistoryDatabase(new ByteArrayInputStream(new byte[0]), folder.newFile()));
    }

    @Test(expected = IOException.class)
    public void damagedZipIsAnError() throws IOException {
        byte[] zip = zip(singleEntry("browserhistory.db", DATABASE)).readAllBytes();
        // Make the first deflate block of the only entry invalid (block type 11).
        int nameLength = (zip[26] & 0xFF) | (zip[27] & 0xFF) << 8;
        int extraLength = (zip[28] & 0xFF) | (zip[29] & 0xFF) << 8;
        zip[30 + nameLength + extraLength] = (byte) 0xFF;
        ExportPackage.extractHistoryDatabase(new ByteArrayInputStream(zip), folder.newFile());
    }

    @Test
    public void historyEntryThatIsNotADatabaseIsRejected() throws IOException {
        assertEquals(ExportPackage.Content.UNKNOWN_FORMAT,
                ExportPackage.extractHistoryDatabase(zip(singleEntry("browserhistory.db", bytes("not a database"))),
                        folder.newFile()));
        assertEquals(ExportPackage.Content.UNKNOWN_FORMAT,
                ExportPackage.extractHistoryDatabase(zip(singleEntry("browserhistory.db", new byte[0])), folder.newFile()));
    }

    @Test(expected = IOException.class)
    public void entryNameThatIsNotUtf8IsAnError() throws IOException {
        // Written as ISO-8859-1 without the UTF-8 flag, so "é" is an invalid UTF-8 byte when read back.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.ISO_8859_1)) {
            zip.putNextEntry(new ZipEntry("café.xml"));
            zip.write(bytes("<map />"));
            zip.closeEntry();
        }
        ExportPackage.extractHistoryDatabase(new ByteArrayInputStream(bytes.toByteArray()), folder.newFile());
    }

    @Test
    public void entryNamesAreNeverUsedAsPaths() throws IOException {
        // A malicious entry name must only ever be matched by its file name, and written to the destination.
        Map<String, byte[]> entries = singleEntry("../../databases/browserhistory.db", DATABASE);
        File destination = folder.newFile();
        assertEquals(ExportPackage.Content.HISTORY, ExportPackage.extractHistoryDatabase(zip(entries), destination));
        assertArrayEquals(DATABASE, Files.readAllBytes(destination.toPath()));
        String[] created = folder.getRoot().list();
        assertEquals("only the destination was written", 1, created.length);
    }

    @Test
    public void matchesHistoryDatabaseNames() {
        assertTrue(ExportPackage.isHistoryDatabase("browserhistory.db"));
        assertTrue(ExportPackage.isHistoryDatabase("a/b/browserhistory.db"));
        assertTrue(ExportPackage.isHistoryDatabase("a\\browserhistory.db"));
        assertFalse(ExportPackage.isHistoryDatabase("browserhistory.db-journal"));
        assertFalse(ExportPackage.isHistoryDatabase("reddits.db"));
        assertFalse(ExportPackage.isHistoryDatabase("browserhistory.db/"));
    }

    private static Map<String, byte[]> singleEntry(String name, byte[] content) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(name, content);
        return entries;
    }

    private static ByteArrayInputStream zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
