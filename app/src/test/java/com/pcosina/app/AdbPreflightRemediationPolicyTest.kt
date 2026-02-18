package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbPreflightRemediationPolicyTest {

    @Test
    fun adbPreflightScript_supportsOptionalAttemptFixPath() {
        val source = read(resolve("scripts", "check_adb_access.ps1"))
        assertTrue(
            "ADB preflight script should expose AttemptFix switch.",
            source.contains("[switch]${'$'}AttemptFix")
        )
        assertTrue(
            "ADB preflight script should attempt to create blocked .android directory when requested.",
            source.contains("adb preflight auto-fix succeeded")
        )
        assertTrue(
            "ADB preflight script should include elevated-session remediation hint when auto-fix fails.",
            source.contains("elevated PowerShell session")
        )
    }

    @Test
    fun connectedRunner_exposesAttemptAdbFixOption() {
        val source = read(resolve("scripts", "run_connected_android_tests.ps1"))
        assertTrue(
            "Connected runner should expose AttemptAdbFix switch.",
            source.contains("[switch]${'$'}AttemptAdbFix")
        )
        assertTrue(
            "Connected runner should forward AttemptAdbFix into adb preflight.",
            source.contains("-AttemptFix:${'$'}AttemptAdbFix")
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
