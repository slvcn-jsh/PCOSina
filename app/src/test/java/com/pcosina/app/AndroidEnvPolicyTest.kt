package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidEnvPolicyTest {

    @Test
    fun androidEnvScript_routesAndroidToolingIntoRepoLocalWritableHomes() {
        val source = read(resolve("scripts", "android-env.ps1"))
        assertTrue(source.contains("GRADLE_USER_HOME"))
        assertTrue(source.contains("ANDROID_USER_HOME"))
        assertTrue(source.contains("HOME"))
        assertTrue(source.contains("USERPROFILE"))
        assertTrue(source.contains("JAVA_TOOL_OPTIONS"))
        assertTrue(source.contains("GRADLE_OPTS"))
        assertTrue(source.contains("Remove-Item Env:ANDROID_PREFS_ROOT"))
        assertTrue(source.contains("Remove-Item Env:ANDROID_SDK_HOME"))
        assertTrue(source.contains("analytics.settings"))
    }

    @Test
    fun adbPreflightScript_sourcesAndroidEnvBeforeCheckingAdb() {
        val source = read(resolve("scripts", "check_adb_access.ps1"))
        assertTrue(source.contains("android-env.ps1"))
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
