package app.revanced.patches.rifimport.shared

/** rif is fun, the free ad-supported app. */
internal const val RIF_FREE_PACKAGE = "com.andrewshu.android.reddit"

/** rif is fun golden platinum, the paid app: the same code as a separate R8 build, so obfuscated names differ. */
internal const val RIF_PLATINUM_PACKAGE = "com.andrewshu.android.redditdonation"

/** rif's final release. */
internal const val RIF_VERSION = "5.6.22"

internal val RIF_COMPATIBILITY = arrayOf(
    RIF_FREE_PACKAGE to setOf(RIF_VERSION),
    RIF_PLATINUM_PACKAGE to setOf(RIF_VERSION),
)

/** rif's settings export/import screen. Not obfuscated (it is declared in the manifest). */
internal const val SETTINGS_EXPORT_IMPORT_ACTIVITY =
    "Lcom/andrewshu/android/reddit/settings/export/SettingsExportImportActivity;"

internal const val EXTENSION = "extensions/rifimport.rve"
internal const val EXTENSION_PACKAGE = "app.revanced.extension.rifimport"
internal const val EXTENSION_TYPE_PREFIX = "Lapp/revanced/extension/rifimport"
