package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckPolicyTest {

    @Test
    fun buildConfig_includesFirebaseAppCheckDependencies() {
        val source = read(resolve("app", "build.gradle.kts"))
        assertTrue(source.contains("libs.firebase.appcheck.playintegrity"))
        assertTrue(source.contains("libs.firebase.appcheck.debug"))
    }

    @Test
    fun app_initializesAppCheckProviderFactory() {
        val source = read(resolve("app", "src", "main", "java", "com", "pcosina", "app", "PcosinaApp.kt"))
        assertTrue(source.contains("FirebaseAppCheck.getInstance()"))
        assertTrue(source.contains("DebugAppCheckProviderFactory.getInstance()"))
        assertTrue(source.contains("PlayIntegrityAppCheckProviderFactory.getInstance()"))
        assertTrue(source.contains("setTokenAutoRefreshEnabled(true)"))
    }

    @Test
    fun mealPlanRepository_attachesAppCheckHeader() {
        val source = read(resolve("app", "src", "main", "java", "com", "pcosina", "app", "data", "repository", "MealPlanRepository.kt"))
        assertTrue(source.contains("FirebaseAppCheck.getInstance()"))
        assertTrue(source.contains("getAppCheckToken(false)"))
        assertTrue(source.contains("addHeader(\"X-Firebase-AppCheck\""))
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
