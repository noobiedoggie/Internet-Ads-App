import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Reads a setting from gradle.properties or an environment variable; blank counts as missing. */
fun cfg(name: String, default: String = ""): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: default

// Google's public test IDs: safe to click, never earn money.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"

val keystoreFile = cfg("KEYSTORE_FILE")

android {
    namespace = "com.gigabee.app"
    compileSdk = 36

    defaultConfig {
        applicationId = cfg("APPLICATION_ID", "com.gigabee.app")
        minSdk = 26
        targetSdk = 36
        versionCode = cfg("VERSION_CODE", "1").toInt()
        versionName = cfg("VERSION_NAME", "1.0.0")

        manifestPlaceholders["admobAppId"] = cfg("ADMOB_APP_ID", testAppId)
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${cfg("ADMOB_REWARDED_ID", testRewardedId)}\"")
        // With a real AdMob app but no banner unit, show no banner (never mix test units into a live app).
        val bannerDefault = if (cfg("ADMOB_APP_ID").isBlank()) testBannerId else ""
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${cfg("ADMOB_BANNER_ID", bannerDefault)}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("SUPABASE_URL").trimEnd('/')}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "BITLABS_TOKEN", "\"${cfg("BITLABS_TOKEN")}\"")
    }

    signingConfigs {
        // Shared debug key (public, not a secret) so every CI build installs over the previous one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystoreFile.isNotBlank()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = cfg("KEYSTORE_PASSWORD")
                keyAlias = cfg("KEY_ALIAS")
                keyPassword = cfg("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile.isNotBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

if (cfg("ADMOB_APP_ID").isBlank()) {
    logger.warn("GigaBee: no ADMOB_APP_ID set, building with Google TEST ads (they earn nothing).")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.android.gms:play-services-ads:24.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    implementation("com.google.zxing:core:3.5.3") // QR code for the eSIM install
    implementation("androidx.work:work-runtime-ktx:2.10.0") // daily reminder
}
