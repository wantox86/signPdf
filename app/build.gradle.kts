plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wantox86.signpdf"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wantox86.signpdf"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public URL behind the homelab's Cloudflare tunnel (mac-mini tunnel ->
        // localhost:8090, where signPDF-Backend's docker-compose runs) -- works from a real
        // device or emulator alike, no host-loopback IP needed.
        buildConfigField("String", "API_BASE_URL", "\"https://signpdf-backend.quezacolt.my.id/\"")
    }

    signingConfigs {
        getByName("debug") {
            // Keystore debug tetap, di-commit ke repo (bukan generated on-the-fly kayak default
            // AGP) -- CI (GitHub Actions runner) itu VM baru tiap run, jadi kalau andelin
            // keystore debug default yang auto-generate, tiap build APK punya signature beda,
            // dan install APK baru di atas yang lama selalu ke-reject Android ("signature
            // conflict"), user kepaksa uninstall dulu tiap kali. Keystore debug bukan rahasia
            // (credential-nya emang publik/well-known), aman di-commit.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        // Robolectric needs the merged manifest/resources available to unit tests (e.g.
        // getString() calls in the ViewModels/repositories under test).
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.coil)
    implementation(libs.pdfbox.android)
    implementation(libs.signature.pad)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
