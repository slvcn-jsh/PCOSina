package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualBaselineChecksumPolicyTest {

    @Test
    fun compactVisualBaselineHashes_matchManifest() {
        val baselineDir = resolve("app", "src", "androidTest", "assets", "visual_baselines")
        val manifest = baselineDir.resolve("manifest.sha256")
        assertTrue("Missing baseline manifest: ${manifest.fileName}", Files.exists(manifest))
        val lines = Files.readAllLines(manifest)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        assertTrue("Baseline manifest should contain entries.", lines.isNotEmpty())
        lines.forEach { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            assertEquals("Malformed manifest line: $line", 2, parts.size)
            val expectedHash = parts[0].lowercase()
            val fileName = parts[1].trim()
            val filePath = baselineDir.resolve(fileName)
            assertTrue("Baseline file in manifest is missing: $fileName", Files.exists(filePath))
            val actualHash = sha256(filePath)
            assertEquals("Baseline hash mismatch for $fileName", expectedHash, actualHash)
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = Files.readAllBytes(path)
        return digest.digest(bytes).joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
