// The extension is plain Java compiled to a DEX file (extensions/rifimport.rve) that the patch merges into rif.
// Like the official ReVanced extension plugin, it is built as an Android app module and its merged release DEX is
// shared with the patches project, which bundles it in the .rvp.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.revanced.extension.rifimport"
    compileSdk = 35

    defaultConfig {
        // rif's minSdk: this code runs inside rif on Android 5+.
        minSdk = 21
        targetSdk = 33
        multiDexEnabled = false
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // The patch rewrites RifTheme.activityTheme() by name, so nothing may be renamed or inlined.
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = false
        resValues = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Fail on platform APIs newer than rif's minSdk without a version check.
    lint {
        checkOnly += setOf("NewApi", "InlinedApi")
        abortOnError = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

val syncExtension = tasks.register<Sync>("syncExtension") {
    description = "Copies the merged release DEX to build/revanced/extensions/rifimport.rve."
    val dexTask = tasks.getByName("mergeDexRelease")
    dependsOn(dexTask)
    from(dexTask.outputs.files.asFileTree.matching { include("**/*.dex") })
    into(layout.buildDirectory.dir("revanced/extensions"))
    rename { "rifimport.rve" }
}

// Consumed by :patches, which adds build/revanced to its resources.
configurations.create("extensionConfiguration") {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(layout.buildDirectory.dir("revanced")) { builtBy(syncExtension) }
}
