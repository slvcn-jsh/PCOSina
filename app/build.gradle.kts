import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.firebase.appdistribution")
    id("com.google.firebase.crashlytics")
}

val googleServicesConfig = file("google-services.json")
val canLoadGoogleServicesConfig = googleServicesConfig.exists() &&
    googleServicesConfig.isFile &&
    googleServicesConfig.canRead()
val releaseBaseUrl = "https://pcosina-backend.onrender.com/"
val defaultDebugBaseUrl = releaseBaseUrl
val debugBaseUrlFromEnv = System.getenv("DEBUG_BASE_URL")
val debugBaseUrlFromProperty = providers.gradleProperty("debugBaseUrl").orNull
val resolvedDebugBaseUrl = (debugBaseUrlFromEnv ?: debugBaseUrlFromProperty ?: defaultDebugBaseUrl).trim()
val defaultSchemaVersion = "1.5.0"
val debugSchemaVersionFromEnv = System.getenv("DEBUG_SCHEMA_VERSION")
val debugSchemaVersionFromProperty = providers.gradleProperty("debugSchemaVersion").orNull
val resolvedDebugSchemaVersion = (debugSchemaVersionFromEnv ?: debugSchemaVersionFromProperty ?: defaultSchemaVersion).trim()
val insecureReleaseSigningFromEnv = System.getenv("PCOSINA_ALLOW_INSECURE_RELEASE_SIGNING")
val insecureReleaseSigningFromProperty = providers.gradleProperty("allowInsecureReleaseSigning").orNull
val allowInsecureReleaseSigning = (
    insecureReleaseSigningFromEnv
        ?: insecureReleaseSigningFromProperty
        ?: "false"
    ).toBooleanStrictOrNull() ?: false
val releaseStoreFilePath = (System.getenv("PCOSINA_RELEASE_STORE_FILE")
    ?: providers.gradleProperty("releaseStoreFile").orNull
    ?: "").trim()
val releaseStorePassword = (System.getenv("PCOSINA_RELEASE_STORE_PASSWORD")
    ?: providers.gradleProperty("releaseStorePassword").orNull
    ?: "").trim()
val releaseKeyAlias = (System.getenv("PCOSINA_RELEASE_KEY_ALIAS")
    ?: providers.gradleProperty("releaseKeyAlias").orNull
    ?: "").trim()
val releaseKeyPassword = (System.getenv("PCOSINA_RELEASE_KEY_PASSWORD")
    ?: providers.gradleProperty("releaseKeyPassword").orNull
    ?: "").trim()
val hasManagedReleaseSigning = releaseStoreFilePath.isNotBlank() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()
val releaseSentryDsn = (
    System.getenv("PCOSINA_ANDROID_SENTRY_DSN")
        ?: providers.gradleProperty("releaseSentryDsn").orNull
        ?: ""
    ).trim()
val allowMissingGoogleServices = providers.gradleProperty("allowMissingGoogleServices")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false
// Optional tuning for uncommon task naming patterns in CI/build tooling.
val releaseTaskIncludePattern = providers.gradleProperty("releaseTaskIncludePattern").orNull
val releaseTaskExcludePattern = providers.gradleProperty("releaseTaskExcludePattern").orNull
val releaseTaskPatterns = listOf(
    Regex(
        pattern = """(^|:)[A-Za-z0-9]*Release[A-Za-z0-9]*$""",
        option = RegexOption.IGNORE_CASE
    ),
    Regex(
        pattern = """(^|:)release$""",
        option = RegexOption.IGNORE_CASE
    )
)
val releaseTasksRequested = gradle.startParameter.taskNames.any { taskName ->
    val releaseLike = releaseTaskPatterns.any { pattern -> pattern.containsMatchIn(taskName) }
    val customInclude = runCatching {
        releaseTaskIncludePattern?.takeIf { it.isNotBlank() }?.let { regex ->
            Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(taskName)
        } ?: false
    }.getOrElse { false }
    val customExclude = runCatching {
        releaseTaskExcludePattern?.takeIf { it.isNotBlank() }?.let { regex ->
            Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(taskName)
        } ?: false
    }.getOrElse { false }
    val normalized = taskName.substringAfterLast(':')
    val nonPackaging = normalized.contains("test", ignoreCase = true) ||
        normalized.contains("lint", ignoreCase = true) ||
        normalized.contains("check", ignoreCase = true) ||
        normalized.contains("verify", ignoreCase = true) ||
        normalized.contains("report", ignoreCase = true) ||
        normalized.contains("analysis", ignoreCase = true)
    (releaseLike || customInclude) && !nonPackaging && !customExclude
}

if (canLoadGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
} else {
    if (releaseTasksRequested && !allowMissingGoogleServices) {
        throw GradleException(
            "google-services.json is missing or unreadable. " +
                "Release tasks require a readable Firebase config. " +
                "If this is a non-packaging false positive, rerun with -PallowMissingGoogleServices=true. " +
                "For custom task names, tune matching with -PreleaseTaskIncludePattern and -PreleaseTaskExcludePattern."
        )
    }
    logger.warn(
        "google-services.json is missing or unreadable; skipping com.google.gms.google-services plugin."
    )
}

if (releaseTasksRequested && !hasManagedReleaseSigning && !allowInsecureReleaseSigning) {
    throw GradleException(
        "Release signing credentials are not configured. " +
            "Set PCOSINA_RELEASE_STORE_FILE / PCOSINA_RELEASE_STORE_PASSWORD / " +
            "PCOSINA_RELEASE_KEY_ALIAS / PCOSINA_RELEASE_KEY_PASSWORD, " +
            "or explicitly allow insecure local signing with PCOSINA_ALLOW_INSECURE_RELEASE_SIGNING=true."
    )
}


android {
    namespace = "com.pcosina.app"
    // API 36 required for current versions of core-ktx and activity-compose
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (hasManagedReleaseSigning) {
                storeFile = file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else if (allowInsecureReleaseSigning) {
                // Explicitly opt-in fallback for local and CI packaging only.
                val androidHome = System.getenv("ANDROID_USER_HOME") ?: "${System.getProperty("user.home")}/.android"
                storeFile = file("$androidHome/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.pcosina.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 39
        versionName = "1.10.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        fun validateBaseUrl(name: String, url: String) {
            val pattern = Regex("^https?://.+/$")
            if (!pattern.matches(url)) {
                throw GradleException("Invalid BASE_URL for $name: $url")
            }
        }
        validateBaseUrl("release", releaseBaseUrl)
        buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
        buildConfigField("String", "SENTRY_DSN", "\"$releaseSentryDsn\"")
        buildConfigField("String", "SCHEMA_VERSION", "\"$defaultSchemaVersion\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val pattern = Regex("^https?://.+/$")
            if (!pattern.matches(releaseBaseUrl)) {
                throw GradleException("Invalid BASE_URL for release: $releaseBaseUrl")
            }
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            buildConfigField("String", "SENTRY_DSN", "\"$releaseSentryDsn\"")
            signingConfig = signingConfigs.getByName("release")
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotesFile = "${rootProject.projectDir}/release-notes.txt"
                val envAppId = System.getenv("FIREBASE_APP_ID")
                if (!envAppId.isNullOrBlank()) {
                    appId = envAppId
                }
                val envCreds = System.getenv("FIREBASE_APPDIST_CREDENTIALS_FILE")
                    ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
                if (!envCreds.isNullOrBlank()) {
                    serviceCredentialsFile = envCreds
                }
                val defaultGroups = providers.gradleProperty("firebaseAppDistributionDefaultGroups").orNull
                val configuredGroups = System.getenv("FIREBASE_APPDIST_GROUPS") ?: defaultGroups
                if (!configuredGroups.isNullOrBlank()) {
                    groups = configuredGroups
                }
                val defaultTesters = providers.gradleProperty("firebaseAppDistributionDefaultTesters").orNull
                val configuredTesters = System.getenv("FIREBASE_APPDIST_TESTERS") ?: defaultTesters
                if (!configuredTesters.isNullOrBlank()) {
                    testers = configuredTesters
                }
            }
        }
        debug {
            val debugBaseUrl = resolvedDebugBaseUrl
            val pattern = Regex("^https?://.+/$")
            if (!pattern.matches(debugBaseUrl)) {
                throw GradleException("Invalid BASE_URL for debug: $debugBaseUrl")
            }
            val schemaPattern = Regex("""^\d+\.\d+\.\d+$""")
            if (!schemaPattern.matches(resolvedDebugSchemaVersion)) {
                throw GradleException("Invalid SCHEMA_VERSION for debug: $resolvedDebugSchemaVersion")
            }
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
            buildConfigField("String", "SENTRY_DSN", "\"\"")
            buildConfigField("String", "SCHEMA_VERSION", "\"$resolvedDebugSchemaVersion\"")
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
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.tasks)
    implementation(libs.play.services.auth)
    implementation(libs.sentry.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
