import java.util.Properties

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
