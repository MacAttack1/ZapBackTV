import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- version: one source of truth in version.properties
val appVersion: String = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
    .getProperty("versionName").trim()
val appVersionCode: Int = appVersion.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }

// ---- signing: every build (phone, PC, GitHub) must use the same key, or Android refuses to update the app.
// The key lives in signing/ (kept out of git). GitHub Actions recreates it from repository secrets.
val keystoreProps = rootProject.file("signing/keystore.properties")
val lumenKey: Properties? = if (keystoreProps.exists()) Properties().apply { keystoreProps.inputStream().use { load(it) } } else null

android {
    namespace = "com.lumen.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumen.iptv" // kept from the first build so updates install over it and keep your playlists
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "GITHUB_REPO", "\"${providers.gradleProperty("lumen.githubRepo").getOrElse("")}\"")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (lumenKey != null) create("lumen") {
            storeFile = rootProject.file("signing/" + lumenKey.getProperty("storeFile"))
            storePassword = lumenKey.getProperty("storePassword")
            keyAlias = lumenKey.getProperty("keyAlias")
            keyPassword = lumenKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("lumen")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to the debug key when signing/ is missing, so a build always produces an installable APK.
            signingConfig = signingConfigs.findByName("lumen") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // Media3 marks many stable-in-practice APIs as @UnstableApi.
        disable += "UnsafeOptInUsageError"
        abortOnError = false
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "ZapBackTV-$appVersion" + (if (buildType.name == "debug") "-debug" else "") + ".apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
