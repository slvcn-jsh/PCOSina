package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRecoveryPolicyTest {

    @Test
    fun progressScreen_keepsLocalUiStateOutOfRememberSaveable() {
        val progress = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "ProgressRefinedScreen.kt"
            )
        )

        assertFalse(
            "Progress screen should not keep screen-local form state in rememberSaveable after the crash investigation.",
            progress.contains("rememberSaveable")
        )
        assertTrue(
            "Progress screen side effects should be guarded so a single bad payload does not crash the route.",
            progress.contains("runCatching") &&
                progress.contains("Failed to compute weekly meal summary safely.")
        )
    }

    @Test
    fun reflectionStore_recoversUnreadableEncryptedPrefs() {
        val store = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "ReflectionStore.kt"
            )
        )

        assertTrue(
            "ReflectionStore should reset unreadable encrypted prefs instead of crashing Progress.",
            store.contains("deleteSharedPreferences(PREFS_NAME)")
        )
        assertTrue(
            "ReflectionStore should guard reads behind a safe fallback path.",
            store.contains("readOrDefault")
        )
        assertTrue(
            "ReflectionStore should guard writes behind a safe fallback path.",
            store.contains("writeSafely")
        )
    }

    @Test
    fun progressViewModel_loadsProgressStateWithSafeFallbacks() {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "ProgressViewModel.kt"
            )
        )

        assertTrue(
            "ProgressViewModel should catch load failures and reset to safe empty state.",
            viewModel.contains("Failed to load progress state safely.")
        )
        assertTrue(
            "ProgressViewModel should catch weekly journal load failures.",
            viewModel.contains("Failed to load weekly journal safely.")
        )
        assertTrue(
            "ProgressViewModel should catch weekly spend load failures.",
            viewModel.contains("Failed to load weekly spend safely.")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallback = Paths.get(parts.drop(1).first(), *parts.drop(2).toTypedArray())
        if (Files.exists(fallback)) return fallback
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
