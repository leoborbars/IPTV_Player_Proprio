import java.util.Properties
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun buildConfigString(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
val defaultProgressEndpoint = localProperties.getProperty(
    "lesil.progressSyncEndpoint",
    localProperties.getProperty("lunastream.progressSyncEndpoint", "http://192.168.3.47:8787")
)
val defaultProgressToken = localProperties.getProperty(
    "lesil.progressSyncToken",
    localProperties.getProperty("lunastream.progressSyncToken", "")
)
val buildTimeMs = System.currentTimeMillis()
val gitRevision = runCatching {
    providers.exec { commandLine("git", "rev-parse", "--short=12", "HEAD") }
        .standardOutput.asText.get().trim()
}.getOrDefault("indisponível")
val buildHash = MessageDigest.getInstance("SHA-256").apply {
    (fileTree("src/main").files + file("build.gradle.kts"))
        .sortedBy { it.relativeTo(rootProject.projectDir).invariantSeparatorsPath }
        .forEach { source ->
            update(source.relativeTo(rootProject.projectDir).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            update(source.readBytes())
        }
}.digest().take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }

android {
    namespace = "br.com.iptvlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.iptvlab"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3-lesil"
        buildConfigField("String", "DEFAULT_PROGRESS_SYNC_ENDPOINT", buildConfigString(defaultProgressEndpoint))
        buildConfigField("String", "DEFAULT_PROGRESS_SYNC_TOKEN", buildConfigString(defaultProgressToken))
        buildConfigField("String", "BUILD_HASH", buildConfigString(buildHash))
        buildConfigField("String", "GIT_REVISION", buildConfigString(gitRevision))
        buildConfigField("long", "BUILD_TIME_MS", "${buildTimeMs}L")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.annotation:annotation:1.9.1")
}
