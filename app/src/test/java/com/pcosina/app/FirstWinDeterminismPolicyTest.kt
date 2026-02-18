package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstWinDeterminismPolicyTest {

    @Test
    fun firstWinInstrumentation_usesDeterministicTimeoutAndOfflineSafeFeedbackClient() {
        val source = read(resolve("app", "src", "androidTest", "java", "com", "pcosina", "app", "FirstWinFlowUiTest.kt"))
        assertTrue(
            "First-win instrumentation should support runner-configurable timeout.",
            source.contains("first_win_timeout_ms")
        )
        assertTrue(
            "First-win instrumentation should emit elapsed-time proof log.",
            source.contains("first_win_elapsed_ms")
        )
        assertTrue(
            "First-win instrumentation should use fast-fail feedback client to avoid network-leak flakiness.",
            source.contains("callTimeout(300") &&
                source.contains("http://127.0.0.1:9/")
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
