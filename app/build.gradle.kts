plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "com.niva.launcher"
    compileSdk = 37
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.niva.launcher"
        minSdk = 28
        targetSdk = 37
        versionCode = 8
        versionName = "1.2.0-niva"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Release signing via environment variables (populated from GitHub Secrets in CI).
            // The keystore file and passwords are NEVER committed to the repository.
            val ksPath = System.getenv("NIVA_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("NIVA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NIVA_KEY_ALIAS")
                keyPassword = System.getenv("NIVA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Map bundled fonts directly; variable weights share the same native
        // font buffer instead of inflating separate copies on the UI thread.
        noCompress += "ttf"
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
    lint {
        // Community translations can be incomplete; Android falls back to English.
        // Keep reporting missing strings without blocking release builds.
        warning += "MissingTranslation"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.material.kolor)
    implementation(libs.aboutlibraries.core)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (Android's built-in org.json is a stub that throws)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

aboutLibraries {
    offlineMode = true
    collect {
        includeTestVariants = false
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
    export {
        excludeFields.add("License.content")
    }
}
