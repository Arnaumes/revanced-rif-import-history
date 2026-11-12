import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "app.revanced"

val repository = "https://github.com/Arnaumes/revanced-rif-import-history"

val about = mapOf(
    "Name" to "rif import history",
    "Description" to "ReVanced patch that adds \"Import history\" to rif is fun 5.6.22 (free and golden platinum): " +
        "it merges the history of a rif \"Export settings AND HISTORY\" backup into the app's history.",
    "Source" to repository,
    "Author" to "Arnaumes",
    "Contact" to "$repository/issues",
    "Website" to repository,
    "License" to "GNU General Public License v3.0",
)

// region ReVanced Patcher API

// ReVanced publishes the patcher only to GitHub Packages, which requires a token even for public packages. The public
// ReVanced CLI release jar bundles the same patcher, so its API is extracted from there. It is only needed to compile:
// ReVanced CLI and ReVanced Manager provide the patcher at runtime.
val revancedCli: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

val revancedCliSha256 = libs.versions.revanced.cli.sha256.get()

val patcherApiJar = tasks.register<Jar>("patcherApiJar") {
    description = "Extracts the ReVanced Patcher API from the ReVanced CLI release jar."
    archiveFileName.set("revanced-patcher-api.jar")
    destinationDirectory.set(layout.buildDirectory.dir("patcher-api"))
    inputs.property("revancedCliSha256", revancedCliSha256)
    doFirst {
        val jar = revancedCli.singleFile
        val sha256 = MessageDigest.getInstance("SHA-256").digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
        check(sha256 == revancedCliSha256) {
            "${jar.name} has SHA-256 $sha256, expected $revancedCliSha256 (gradle/libs.versions.toml)"
        }
    }
    from({ zipTree(revancedCli.singleFile) }) {
        include(
            "app/revanced/patcher/**",
            "app/revanced/com/**",
            "app/revanced/collections/**",
            "app/revanced/java/**",
            "META-INF/patcher.kotlin_module",
        )
    }
}

dependencies {
    revancedCli(variantOf(libs.revanced.cli) { classifier("all") })
    compileOnly(files(patcherApiJar))
    compileOnly(libs.smali)
}

// endregion

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xexplicit-backing-fields", "-Xcontext-parameters")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// `-Pe2e` adds the patches used for end-to-end testing (see docs/testing.md). Never in a release.
if (providers.gradleProperty("e2e").isPresent) {
    kotlin.sourceSets.named("main") { kotlin.srcDir("src/e2e/kotlin") }
}

// region Extension

// The extension DEX (extensions/rifimport.rve) becomes a resource of the patches, where extendWith() finds it.
val extensionFiles: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    extensionFiles(project(mapOf("path" to ":extensions:rifimport", "configuration" to "extensionConfiguration")))
}

sourceSets.main {
    resources.srcDir(extensionFiles)
}

// endregion

// region Patches file (.rvp)

fun Jar.rvpManifest() = manifest {
    attributes(about + mapOf("Version" to project.version.toString(), "Timestamp" to System.currentTimeMillis().toString()))
}

// JVM-only bundle (class files), as built by `jar` in the official template. Intermediate: buildAndroid adds the DEX.
tasks.jar {
    archiveClassifier.set("jvm")
    archiveExtension.set("rvp")
    destinationDirectory.set(layout.buildDirectory.dir("revanced/jvm"))
    rvpManifest()
}

val d8: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    d8(libs.r8)
}

val classesJar = tasks.register<Jar>("classesJar") {
    description = "Packs only the compiled patch classes, the input of D8."
    archiveFileName.set("classes.jar")
    destinationDirectory.set(layout.buildDirectory.dir("revanced/d8"))
    from(sourceSets.main.get().output.classesDirs)
}

val dexPatches = tasks.register<JavaExec>("dexPatches") {
    description = "Compiles the patches to DEX with D8, like the official ReVanced patches Gradle plugin."
    val input = classesJar.flatMap { it.archiveFile }
    val output = layout.buildDirectory.file("revanced/d8/classes.zip")
    inputs.file(input)
    outputs.file(output)
    classpath = d8
    mainClass.set("com.android.tools.r8.D8")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--release",
                "--min-api", "26",
                "--output", output.get().asFile.absolutePath,
                input.get().asFile.absolutePath,
            )
        },
    )
}

// ReVanced Manager (Android) loads patches from a classes.dex inside the .rvp; ReVanced CLI loads the class files.
// This bundle has both, so it works with either.
val buildAndroid = tasks.register<Jar>("buildAndroid") {
    group = "build"
    description = "Builds patches-<version>.rvp with the patch classes, their DEX and the extension."
    archiveBaseName.set("patches")
    archiveExtension.set("rvp")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    rvpManifest()
    from(tasks.jar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(dexPatches.map { zipTree(it.outputs.files.singleFile) })
}

tasks.assemble {
    dependsOn(buildAndroid)
}

// endregion
