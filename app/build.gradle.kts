import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.pcosina.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pcosina.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 14
        versionName = "1.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "BASE_URL", "\"http://192.168.1.48:8000/\"")
        buildConfigField("String", "SENTRY_DSN", "\"https://91e7fe2e7e460b73f1649eb8f8b39b22@o4510823495434240.ingest.us.sentry.io/4510823509983232\"")
    }

    buildTypes {
        release {
            // Use debug signing for internal testing to ensure APK installs.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://pcosina-backend.onrender.com/\"")
            buildConfigField("String", "SENTRY_DSN", "\"https://91e7fe2e7e460b73f1649eb8f8b39b22@o4510823495434240.ingest.us.sentry.io/4510823509983232\"")
            firebaseAppDistribution {
                appId = "1:950408114415:android:0b3c55b663b7638c20ab1a"
                groups = "QUADRANT"
                artifactType = "APK"
                releaseNotes = "Release build from dev branch."
            }
        }
        debug {
            buildConfigField("String", "BASE_URL", "\"http://192.168.1.48:8000/\"")
            buildConfigField("String", "SENTRY_DSN", "\"https://91e7fe2e7e460b73f1649eb8f8b39b22@o4510823495434240.ingest.us.sentry.io/4510823509983232\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.8.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("io.sentry:sentry-android:7.10.0")

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
    implementation("com.google.android.material:material:1.12.0")
    
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
