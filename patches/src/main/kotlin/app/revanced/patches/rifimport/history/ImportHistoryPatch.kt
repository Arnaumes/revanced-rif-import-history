package app.revanced.patches.rifimport.history

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableClassDef
import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.firstClassDefOrNull
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.rifimport.shared.EXTENSION
import app.revanced.patches.rifimport.shared.EXTENSION_PACKAGE
import app.revanced.patches.rifimport.shared.EXTENSION_TYPE_PREFIX
import app.revanced.patches.rifimport.shared.RIF_COMPATIBILITY
import app.revanced.patches.rifimport.shared.RIF_FREE_PACKAGE
import app.revanced.patches.rifimport.shared.SETTINGS_EXPORT_IMPORT_ACTIVITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private const val IMPORT_ACTIVITY_CLASS = "$EXTENSION_PACKAGE.ImportHistoryActivity"
private const val IMPORT_ACTIVITY_TYPE = "$EXTENSION_TYPE_PREFIX/ImportHistoryActivity;"
private const val RIF_THEME_TYPE = "$EXTENSION_TYPE_PREFIX/RifTheme;"
private const val FRAMEWORK_ACTIVITY = "Landroid/app/Activity;"

private const val BACKUP_PREFERENCES = "res/xml/backup_preferences.xml"
private const val SETTINGS_IMPORT_KEY = "SETTINGS_IMPORT"
private const val IMPORT_HISTORY_KEY = "REVANCED_IMPORT_HISTORY"

/**
 * Registers the import screen and adds its entry to Settings → Backup, right below rif's "Import settings".
 */
private val importHistoryResourcePatch = resourcePatch {
    compatibleWith(*RIF_COMPATIBILITY)

    apply {
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("AndroidManifest.xml has no <application>")

            val activities = application.getElementsByTagName("activity")
            val registered = (0 until activities.length).any { index ->
                (activities.item(index) as Element).getAttribute("android:name") == IMPORT_ACTIVITY_CLASS
            }
            if (!registered) {
                application.appendChild(
                    manifest.createElement("activity").apply {
                        setAttribute("android:name", IMPORT_ACTIVITY_CLASS)
                        setAttribute("android:label", "Import history")
                        setAttribute("android:exported", "false")
                        // Don't recreate the screen on rotation and similar changes. (A running import also
                        // survives recreation, but this keeps the screen as it is.)
                        setAttribute(
                            "android:configChanges",
                            "density|fontScale|keyboard|keyboardHidden|layoutDirection|locale|orientation|" +
                                "screenLayout|screenSize|smallestScreenSize|uiMode",
                        )
                    },
                )
            }
        }

        document(BACKUP_PREFERENCES).use { preferences ->
            val entries = preferences.getElementsByTagName("Preference")
            val all = (0 until entries.length).map { entries.item(it) as Element }
            if (all.any { it.getAttribute("android:key") == IMPORT_HISTORY_KEY }) return@use

            val importSettings = all.firstOrNull { it.getAttribute("android:key") == SETTINGS_IMPORT_KEY }
                ?: throw PatchException("$BACKUP_PREFERENCES has no \"Import settings\" entry; is this rif is fun 5.6.22?")

            val importHistory = preferences.createElement("Preference").apply {
                setAttribute("android:title", "Import history")
                setAttribute("android:key", IMPORT_HISTORY_KEY)
                setAttribute(
                    "android:summary",
                    "Add the history of a settings and history backup .zip. Keeps your current history.",
                )
                appendChild(
                    preferences.createElement("intent").apply {
                        // Like rif's own entries: rif's settings screens point intents for this package at the
                        // installed package (golden platinum or a renamed one) when they open.
                        setAttribute("android:targetPackage", RIF_FREE_PACKAGE)
                        setAttribute("android:targetClass", IMPORT_ACTIVITY_CLASS)
                    },
                )
            }
            importSettings.parentNode.insertBefore(importHistory, importSettings.nextSibling)
        }
    }
}

@Suppress("unused")
val importHistoryPatch = bytecodePatch(
    name = "Import history",
    description = "Adds \"Import history\" to Settings → Backup. It adds the history of a backup made with " +
        "\"Export settings AND HISTORY\" to rif's history: items already in the history are skipped and nothing " +
        "is removed.",
) {
    compatibleWith(*RIF_COMPATIBILITY)
    dependsOn(importHistoryResourcePatch)
    extendWith(EXTENSION)

    apply {
        val exportImportActivity = firstClassDefOrNull(SETTINGS_EXPORT_IMPORT_ACTIVITY)
            ?: throw PatchException("rif's settings export/import screen was not found; is this rif is fun 5.6.22?")

        // Make the import screen a rif screen like the export/import screen: same base activity (action bar,
        // rotation setting, theme changes) and same theme. The extension is compiled against android.app.Activity,
        // an ancestor of rif's base activity, so its code works with either.
        val rifBaseActivity = exportImportActivity.superclass
            ?: throw PatchException("$SETTINGS_EXPORT_IMPORT_ACTIVITY has no superclass")
        rebase(extensionClass(IMPORT_ACTIVITY_TYPE), rifBaseActivity)

        val (settings, themeGetter) = exportImportActivity.themeLookup()
        extensionClass(RIF_THEME_TYPE).method("activityTheme").addInstructions(
            0,
            """
                invoke-static { }, $settings
                move-result-object v0
                invoke-virtual { v0 }, $themeGetter
                move-result v0
                return v0
            """,
        )
    }
}

/**
 * Makes [activity], compiled as a subclass of android.app.Activity, extend [superclass] instead.
 *
 * Calls to overridden methods (invoke-super) already reach the new superclass, but constructors call the superclass
 * constructor directly (invoke-direct). Left as is, they would skip the constructors of rif's base activity and of
 * the AndroidX activities it extends, whose fields would stay null (a crash in ComponentActivity.onCreate).
 */
private fun BytecodePatchContext.rebase(activity: MutableClassDef, superclass: String) {
    val base = firstClassDefOrNull(superclass) ?: throw PatchException("$superclass not found")
    activity.setSuperClass(superclass)

    var rewritten = 0
    activity.methods.filter { it.name == "<init>" }.forEach { constructor ->
        val index = constructor.instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.INVOKE_DIRECT && instruction.methodReference()?.let {
                it.definingClass == FRAMEWORK_ACTIVITY && it.name == "<init>"
            } == true
        }
        if (index < 0) return@forEach // Delegates to another constructor of the class.

        val call = constructor.instructions[index] as? FiveRegisterInstruction
            ?: throw PatchException("${activity.type}: unexpected constructor call at $index")
        val parameters = call.methodReference()!!.parameterTypes.joinToString("")
        if (base.methods.none { it.name == "<init>" && it.parameterTypes.joinToString("") == parameters }) {
            throw PatchException("$superclass has no <init>($parameters)V constructor")
        }
        val registers = listOf(call.registerC, call.registerD, call.registerE, call.registerF, call.registerG)
            .take(call.registerCount)
            .joinToString { "v$it" }
        constructor.replaceInstruction(index, "invoke-direct { $registers }, $superclass-><init>($parameters)V")
        rewritten++
    }
    if (rewritten == 0) throw PatchException("${activity.type} has no constructor calling the Activity constructor")
}

private fun BytecodePatchContext.extensionClass(type: String): MutableClassDef =
    firstClassDefOrNull(type) ?: throw PatchException("Extension class $type is missing")

private fun MutableClassDef.method(name: String): MutableMethod =
    methods.firstOrNull { it.name == name } ?: throw PatchException("$type->$name not found")

/**
 * Finds the theme lookup at the start of SettingsExportImportActivity.onCreate:
 *
 * ```
 * invoke-static {}, <settings class>->getInstance()<settings class>
 * move-result-object vA
 * invoke-virtual {vA}, <settings class>->getActivityTheme()I
 * move-result vB
 * invoke-virtual {p0, vB}, ...->setTheme(I)V
 * ```
 *
 * and returns the two method references (obfuscated, so they differ between the free and golden platinum builds).
 */
private fun MutableClassDef.themeLookup(): Pair<String, String> {
    val onCreate = methods.firstOrNull { method ->
        method.name == "onCreate" && method.returnType == "V" &&
            method.parameterTypes.map { it.toString() } == listOf("Landroid/os/Bundle;")
    } ?: throw PatchException("$type->onCreate(Bundle) not found")

    val instructions = onCreate.instructions
    val setTheme = instructions.indexOfFirst { instruction ->
        instruction.opcode == Opcode.INVOKE_VIRTUAL && instruction.methodReference()?.let {
            it.name == "setTheme" && it.returnType == "V" && it.parameterTypes.map(CharSequence::toString) == listOf("I")
        } == true
    }
    if (setTheme < 4) throw PatchException("$type->onCreate: theme lookup not found")

    val getter = instructions[setTheme - 2].takeIf { it.opcode == Opcode.INVOKE_VIRTUAL }?.methodReference()
    val instance = instructions[setTheme - 4].takeIf { it.opcode == Opcode.INVOKE_STATIC }?.methodReference()
    val matches = getter != null && instance != null &&
        instructions[setTheme - 1].opcode == Opcode.MOVE_RESULT &&
        instructions[setTheme - 3].opcode == Opcode.MOVE_RESULT_OBJECT &&
        getter.returnType == "I" && getter.parameterTypes.isEmpty() &&
        instance.parameterTypes.isEmpty() && instance.returnType == getter.definingClass
    if (!matches) throw PatchException("$type->onCreate: unexpected theme lookup")

    return instance.toString() to getter.toString()
}

private fun Instruction.methodReference() = (this as? ReferenceInstruction)?.reference as? MethodReference
