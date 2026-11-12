package app.revanced.extension.rifimport;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Texts of the import screen. rif is not translated beyond English, so neither is this.
 */
final class ImportMessages {
    static final String TITLE = "Import history";
    static final String CHOOSE_FILE = "Choose the .zip file created by “Export settings AND HISTORY”.";
    static final String IMPORTING = "Importing history…";
    static final String PLEASE_WAIT = "Please wait until the import finishes.";
    static final String NO_FILE_PICKER = "No app to choose files with was found.";

    static final String NO_HISTORY = "This backup doesn't contain any history.\n\n"
            + "To include it, back up with “Export settings AND HISTORY” (Settings → Backup) "
            + "instead of “Export settings”.";

    static final String NOT_A_BACKUP = "This file isn't a rif backup, or it is damaged.\n\n"
            + "Choose the .zip file created by “Export settings AND HISTORY”, or a browserhistory.db file.";

    static final String INTERRUPTED = "The import was interrupted.\n\n"
            + "Open Import history again to finish it: items that are already in your history are skipped.";

    private ImportMessages() {
    }

    static String imported(int imported, int alreadyPresent, int duplicates, Locale locale) {
        NumberFormat number = NumberFormat.getIntegerInstance(locale);

        String message;
        if (imported == 0) {
            message = alreadyPresent == 0
                    ? "The history in this backup is empty. Nothing was imported."
                    : "Your history already has all " + items(number, alreadyPresent, "item", "items")
                    + " in this backup. Nothing was imported.";
        } else {
            message = "Imported " + items(number, imported, "new history item", "new history items") + ".";
            if (alreadyPresent > 0) {
                message += "\n\n" + items(number, alreadyPresent, "item was", "items were")
                        + " already in your history and stayed unchanged.";
            }
        }
        if (duplicates > 0) {
            message += "\n\n" + items(number, duplicates, "duplicate item", "duplicate items")
                    + " in the backup " + (duplicates == 1 ? "was" : "were") + " skipped.";
        }
        return message;
    }

    static String error(Throwable error) {
        String reason = error.getMessage();
        if (reason == null || reason.isEmpty()) reason = error.getClass().getSimpleName();
        return "Couldn't import the history.\n\n" + reason;
    }

    private static String items(NumberFormat number, int count, String singular, String plural) {
        return number.format(count) + " " + (count == 1 ? singular : plural);
    }
}
