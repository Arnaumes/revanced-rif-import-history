package app.revanced.extension.rifimport;

/**
 * rif's current activity theme (light, dark, ...), so the import screen looks like the rest of rif.
 */
public final class RifTheme {
    private RifTheme() {
    }

    /**
     * Returns the theme resource rif's own export/import screen uses, or 0 if unknown.
     * <p>
     * The "Import history" patch replaces this body with the theme lookup rif's export/import screen does at the
     * start of its onCreate (the obfuscated names differ between the free and golden platinum builds).
     */
    public static int activityTheme() {
        return 0;
    }
}
