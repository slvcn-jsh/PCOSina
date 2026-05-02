package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestSecurityPolicyTest {

    @Test
    fun manifest_disablesGlobalBackupAndCleartext() {
        val source = read(resolve("app", "src", "main", "AndroidManifest.xml"))
        assertTrue(source.contains("android:allowBackup=\"false\""))
        assertTrue(source.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(source.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(source.contains("android:name=\"io.sentry.auto-init\""))
        assertTrue(source.contains("android:value=\"false\""))
    }

    @Test
    fun debug_manifest_allows_local_cleartext_backend_only_for_debug() {
        val manifest = read(resolve("app", "src", "debug", "AndroidManifest.xml"))
        val networkConfig = read(resolve("app", "src", "debug", "res", "xml", "network_security_config.xml"))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"true\""))
        assertTrue(manifest.contains("android:name=\"io.sentry.enabled\""))
        assertTrue(manifest.contains("android:value=\"false\""))
        assertTrue(manifest.contains("android:name=\"io.sentry.android.core.SentryInitProvider\""))
        assertTrue(manifest.contains("tools:node=\"remove\""))
        assertTrue(networkConfig.contains("cleartextTrafficPermitted=\"true\""))
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
