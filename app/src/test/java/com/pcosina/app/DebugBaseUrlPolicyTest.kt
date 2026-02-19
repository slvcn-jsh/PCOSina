package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBaseUrlPolicyTest {

    @Test
    fun debugBaseUrl_isExternalized_withSafeFallback() {
        val source = read(resolve("app", "build.gradle.kts"))
        assertTrue(
            "Build script should support DEBUG_BASE_URL env override for debug builds.",
            source.contains("System.getenv(\"DEBUG_BASE_URL\")")
        )
        assertTrue(
            "Build script should support Gradle property override for debugBaseUrl.",
            source.contains("providers.gradleProperty(\"debugBaseUrl\")")
        )
        assertTrue(
            "Build script should keep a safe emulator fallback base URL.",
            source.contains("http://10.0.2.2:8000/")
        )
        assertTrue(
            "Debug build BASE_URL should be sourced from resolved debug base URL instead of hardcoded LAN IP.",
            source.contains("val debugBaseUrl = resolvedDebugBaseUrl")
        )
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
