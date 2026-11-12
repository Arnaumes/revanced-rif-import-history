package app.revanced.extension.rifimport;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.util.Locale;

public class ImportMessagesTest {
    @Test
    public void newItemsOnly() {
        assertEquals("Imported 12,345 new history items.", ImportMessages.imported(12345, 0, 0, Locale.US));
        assertEquals("Imported 1 new history item.", ImportMessages.imported(1, 0, 0, Locale.US));
    }

    @Test
    public void newAndExistingItems() {
        assertEquals("Imported 12,342 new history items.\n\n3 items were already in your history and stayed unchanged.",
                ImportMessages.imported(12342, 3, 0, Locale.US));
        assertEquals("Imported 2 new history items.\n\n1 item was already in your history and stayed unchanged.",
                ImportMessages.imported(2, 1, 0, Locale.US));
    }

    @Test
    public void duplicatesInTheBackupAreReportedSeparately() {
        assertEquals("Imported 1 new history item.\n\n9 duplicate items in the backup were skipped.",
                ImportMessages.imported(1, 0, 9, Locale.US));
        assertEquals("Imported 5 new history items.\n\n2 items were already in your history and stayed unchanged."
                        + "\n\n1 duplicate item in the backup was skipped.",
                ImportMessages.imported(5, 2, 1, Locale.US));
        assertEquals("Your history already has all 4 items in this backup. Nothing was imported."
                        + "\n\n2 duplicate items in the backup were skipped.",
                ImportMessages.imported(0, 4, 2, Locale.US));
    }

    @Test
    public void nothingNew() {
        assertEquals("Your history already has all 12,345 items in this backup. Nothing was imported.",
                ImportMessages.imported(0, 12345, 0, Locale.US));
        assertEquals("The history in this backup is empty. Nothing was imported.",
                ImportMessages.imported(0, 0, 0, Locale.US));
    }

    @Test
    public void usesLocaleDigitGrouping() {
        assertEquals("Imported 12.345 new history items.", ImportMessages.imported(12345, 0, 0, Locale.GERMANY));
    }

    @Test
    public void errorShowsReason() {
        assertEquals("Couldn't import the history.\n\nThe zip file is damaged",
                ImportMessages.error(new IOException("The zip file is damaged")));
        assertEquals("Couldn't import the history.\n\nIllegalStateException", ImportMessages.error(new IllegalStateException()));
    }
}
