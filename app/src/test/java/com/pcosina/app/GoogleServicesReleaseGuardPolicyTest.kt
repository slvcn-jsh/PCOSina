package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleServicesReleaseGuardPolicyTest {

    @Test
    fun gradleBuild_hasReleaseFailFastWhenGoogleServicesConfigMissing() {
        val source = read(resolve("app", "build.gradle.kts"))
        assertTrue(
            "Build script should evaluate whether google-services.json can be read.",
            source.contains("canLoadGoogleServicesConfig")
        )
        assertTrue(
            "Build script should guard release tasks when google-services.json is unreadable.",
            source.contains("releaseTasksRequested") &&
                source.contains("Release tasks require a readable Firebase config.")
        )
        assertTrue(
            "Build script should avoid guard false positives for non-packaging lint/test tasks.",
            source.contains("nonPackaging") &&
                source.contains("contains(\"test\"") &&
                source.contains("contains(\"lint\"") &&
                source.contains("contains(\"check\"") &&
                source.contains("contains(\"verify\"") &&
                source.contains("contains(\"report\"") &&
                source.contains("contains(\"analysis\"")
        )
        assertTrue(
            "Release task patterns should use generic release segment matching to reduce drift.",
            source.contains("[A-Za-z0-9]*Release[A-Za-z0-9]*")
        )
        assertTrue(
            "Build script should document property override for unexpected release-like task names.",
            source.contains("-PallowMissingGoogleServices=true") &&
                source.contains("releaseTaskIncludePattern") &&
                source.contains("releaseTaskExcludePattern")
        )
        assertTrue(
            "Build script should keep conditional application of Google Services plugin.",
            source.contains("apply(plugin = \"com.google.gms.google-services\")")
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
