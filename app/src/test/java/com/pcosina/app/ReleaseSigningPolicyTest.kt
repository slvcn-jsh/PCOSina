package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningPolicyTest {

    @Test
    fun releaseSigning_requiresExplicitCredentialsOrExplicitInsecureOptIn() {
        val source = read(resolve("app", "build.gradle.kts"))
        assertTrue(source.contains("PCOSINA_RELEASE_STORE_FILE"))
        assertTrue(source.contains("PCOSINA_RELEASE_STORE_PASSWORD"))
        assertTrue(source.contains("PCOSINA_RELEASE_KEY_ALIAS"))
        assertTrue(source.contains("PCOSINA_RELEASE_KEY_PASSWORD"))
        assertTrue(source.contains("PCOSINA_ALLOW_INSECURE_RELEASE_SIGNING"))
    }

    @Test
    fun releaseScript_usesRepoAndroidEnvAndExplicitLocalOptIn() {
        val source = read(resolve("scripts", "release.ps1"))
        assertTrue(source.contains("android-env.ps1"))
        assertTrue(source.contains("[switch]${'$'}AllowInsecureLocalSigning"))
        assertTrue(source.contains("PCOSINA_ALLOW_INSECURE_RELEASE_SIGNING"))
        assertTrue(source.contains(":app:assembleRelease"))
        assertTrue(source.contains("[switch]${'$'}SkipFirebaseDistribution"))
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
