plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.pcosina.app"
    // API 36 required for current versions of core-ktx and activity-compose
    compileSdk = 36

    signingConfigs {
        create("release") {
            // Test-only signing config using the default debug keystore.
            // This makes the release APK installable for Firebase App Distribution.
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.pcosina.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 32
        versionName = "1.09.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val releaseBaseUrl = "https://pcosina-backend.onrender.com/"
        fun validateBaseUrl(name: String, url: String) {
            val pattern = Regex("^https?://.+/$")
            if (!pattern.matches(url)) {
                throw GradleException("Invalid BASE_URL for $name: $url")
            }
        }
        validateBaseUrl("release", releaseBaseUrl)
        buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
        buildConfigField("String", "SENTRY_DSN", "\"\"")
        buildConfigField("String", "SCHEMA_VERSION", "\"1.0.1\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseBaseUrl = "https://pcosina-backend.onrender.com/"
            val pattern = Regex("^https?://.+/$")
            if (!pattern.matches(releaseBaseUrl)) {
                throw GradleException("Invalid BASE_URL for release: $releaseBaseUrl")
            }
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            val debugBaseUrl = "http://192.168.1.48:8000/"
            val pattern = Regex("^https?://.+/$")
            if (!pattern.matches(debugBaseUrl)) {
                throw GradleException("Invalid BASE_URL for debug: $debugBaseUrl")
            }
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // CORRECTED: All hyphens replaced with dots for Kotlin DSL compatibility
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.google.material)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.tasks)
    implementation(libs.sentry.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
