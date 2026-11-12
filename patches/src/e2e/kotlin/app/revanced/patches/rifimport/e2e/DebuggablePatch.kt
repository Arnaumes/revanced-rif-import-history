package app.revanced.patches.rifimport.e2e

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * End-to-end testing only: makes the patched app debuggable, so `adb shell run-as` can read and seed rif's
 * databases. Only compiled with `./gradlew buildAndroid -Pe2e`, and off by default even then. See docs/testing.md.
 */
@Suppress("unused")
val debuggableForTestingPatch = resourcePatch(
    name = "Debuggable (testing only)",
    description = "Makes the app debuggable for end-to-end tests. Never use it for an app you use.",
    use = false,
) {
    apply {
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("AndroidManifest.xml has no <application>")
            application.setAttribute("android:debuggable", "true")
        }
    }
}
