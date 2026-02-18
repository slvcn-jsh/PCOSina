package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedTestRunnerPreflightPolicyTest {

    @Test
    fun connectedTestRunnerScript_invokesAdbPreflightAndConnectedTask() {
        val source = read(resolve("scripts", "run_connected_android_tests.ps1"))
        assertTrue(
            "Connected test runner should invoke adb preflight script.",
            source.contains("check_adb_access.ps1")
        )
        assertTrue(
            "Connected test runner should execute connectedDebugAndroidTest.",
            source.contains("connectedDebugAndroidTest")
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
