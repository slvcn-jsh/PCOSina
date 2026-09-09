import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.firebase.appdistribution")
    id("com.google.firebase.crashlytics")
}

tasks.register<Exec>("verifyDesignContract") {
    group = "verification"
    description = "Verifies generated Android/web design tokens and screen parity metadata."
    workingDir(rootProject.projectDir)
    commandLine("python", "scripts/generate_design_contract.py", "--check")
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
        pattern = """(^|:)[A-Za-z0-9]*Staging[A-Za-z0-9]*$""",
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

fun googleServicesClient(): Map<*, *> {
    if (!canLoadGoogleServicesConfig) {
        throw GradleException("google-services.json is missing or unreadable.")
    }
    val parsed = JsonSlurper().parse(googleServicesConfig) as Map<*, *>
    val clients = parsed["client"] as? List<*> ?: emptyList<Any>()
    return clients
        .filterIsInstance<Map<*, *>>()
        .firstOrNull { client ->
            val info = client["client_info"] as? Map<*, *>
            val androidInfo = info?.get("android_client_info") as? Map<*, *>
            androidInfo?.get("package_name") == "com.pcosina.app"
        }
        ?: throw GradleException("google-services.json has no Android client for com.pcosina.app.")
}

fun googleServicesAndroidSha1s(): Set<String> {
    val client = googleServicesClient()
    val oauthClients = client["oauth_client"] as? List<*> ?: emptyList<Any>()
    return oauthClients
        .filterIsInstance<Map<*, *>>()
        .mapNotNull { oauth ->
            val androidInfo = oauth["android_info"] as? Map<*, *>
            androidInfo?.get("certificate_hash")?.toString()
        }
        .map { it.replace(":", "").lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
}

fun defaultDebugKeystore(): File {
    val androidUserHome = System.getenv("ANDROID_USER_HOME")
        ?: "${System.getProperty("user.home")}/.android"
    return file("$androidUserHome/debug.keystore")
}

fun debugKeystoreSha1(): String? {
    val keystore = defaultDebugKeystore()
    if (!keystore.exists()) return null
    val output = ByteArrayOutputStream()
    val result = exec {
        isIgnoreExitValue = true
        commandLine(
            "keytool",
            "-list",
            "-v",
            "-keystore",
            keystore.absolutePath,
            "-alias",
            "androiddebugkey",
            "-storepass",
            "android",
            "-keypass",
            "android"
        )
        standardOutput = output
        errorOutput = output
    }
    if (result.exitValue != 0) return null
    return Regex("""SHA1:\s*([0-9A-Fa-f:]+)""")
        .find(output.toString())
        ?.groupValues
        ?.get(1)
        ?.replace(":", "")
        ?.lowercase()
}

tasks.register("printGoogleSignInConfig") {
    group = "verification"
    description = "Prints the Firebase Google Sign-In client IDs and local debug signing SHA-1."
    doLast {
        val client = googleServicesClient()
        val oauthClients = client["oauth_client"] as? List<*> ?: emptyList<Any>()
        val webClientId = oauthClients
            .filterIsInstance<Map<*, *>>()
            .firstOrNull { it["client_type"]?.toString() == "3" }
            ?.get("client_id")
            ?.toString()
            .orEmpty()
        val androidSha1s = googleServicesAndroidSha1s()
        val localDebugSha1 = debugKeystoreSha1().orEmpty()
        logger.lifecycle("Firebase project: pcosina")
        logger.lifecycle("Android package: com.pcosina.app")
        logger.lifecycle("Web client ID: $webClientId")
        logger.lifecycle("Configured Android SHA-1 fingerprints:")
        androidSha1s.sorted().forEach { logger.lifecycle("  $it") }
        logger.lifecycle("Local debug keystore SHA-1: ${localDebugSha1.ifBlank { "not found" }}")
        if (localDebugSha1.isNotBlank() && localDebugSha1 !in androidSha1s) {
            logger.lifecycle(
                "Missing Firebase fingerprint. Add $localDebugSha1 to Firebase app " +
                    "1:950408114415:android:0b3c55b663b7638c20ab1a, then download a fresh google-services.json."
            )
        }
    }
}

tasks.register("verifyGoogleSignInDebugSha") {
    group = "verification"
    description = "Fails when the local debug keystore SHA-1 is not registered in google-services.json."
    doLast {
        val localDebugSha1 = debugKeystoreSha1()
            ?: throw GradleException("Could not read the local Android debug keystore SHA-1.")
        val configuredSha1s = googleServicesAndroidSha1s()
        if (localDebugSha1 !in configuredSha1s) {
            throw GradleException(
                "Local debug SHA-1 $localDebugSha1 is missing from app/google-services.json. " +
                    "Register this SHA-1 in Firebase for com.pcosina.app and download a fresh google-services.json."
            )
        }
    }
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
        versionCode = 49
        versionName = "1.10.13"

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
        buildConfigField("String", "APP_ENVIRONMENT", "\"production\"")
        buildConfigField("boolean", "PCOSINA_SEND_APP_CHECK", "true")
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
            buildConfigField("String", "APP_ENVIRONMENT", "\"production\"")
            buildConfigField("boolean", "PCOSINA_SEND_APP_CHECK", "true")
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
        create("staging") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-staging"
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            buildConfigField("String", "SENTRY_DSN", "\"$releaseSentryDsn\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"staging\"")
            buildConfigField("boolean", "PCOSINA_SEND_APP_CHECK", "false")
            signingConfig = signingConfigs.getByName("release")
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotesFile = "${rootProject.projectDir}/release-notes/respondent-test-notes.txt"
                val envAppId = System.getenv("FIREBASE_APP_ID")
                if (!envAppId.isNullOrBlank()) {
                    appId = envAppId
                }
                val envCreds = System.getenv("FIREBASE_APPDIST_CREDENTIALS_FILE")
                    ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
                if (!envCreds.isNullOrBlank()) {
                    serviceCredentialsFile = envCreds
                }
                val defaultGroups = providers.gradleProperty("firebaseAppDistributionRespondentGroups").orNull
                    ?: providers.gradleProperty("firebaseAppDistributionDefaultGroups").orNull
                val configuredGroups = System.getenv("FIREBASE_APPDIST_GROUPS") ?: defaultGroups
                if (!configuredGroups.isNullOrBlank()) {
                    groups = configuredGroups
                }
                val defaultTesters = providers.gradleProperty("firebaseAppDistributionRespondentTesters").orNull
                    ?: providers.gradleProperty("firebaseAppDistributionDefaultTesters").orNull
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
            buildConfigField("String", "APP_ENVIRONMENT", "\"debug\"")
            buildConfigField("boolean", "PCOSINA_SEND_APP_CHECK", "true")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)

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
