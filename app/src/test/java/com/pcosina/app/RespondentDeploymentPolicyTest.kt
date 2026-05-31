package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class RespondentDeploymentPolicyTest {

    @Test
    fun stagingBuild_targetsRenderAndSendsAppCheckLikeRelease() {
        val source = read(resolve("app", "build.gradle.kts"))
        assertTrue(source.contains("create(\"staging\")"))
        assertTrue(source.contains("versionNameSuffix = \"-staging\""))
        assertTrue(source.contains("buildConfigField(\"String\", \"BASE_URL\", \"\\\"${'$'}releaseBaseUrl\\\"\")"))
        assertTrue(source.contains("buildConfigField(\"String\", \"APP_ENVIRONMENT\", \"\\\"staging\\\"\")"))
        assertTrue(source.contains("buildConfigField(\"boolean\", \"PCOSINA_SEND_APP_CHECK\", \"true\")"))
        assertTrue(source.contains("release-notes/respondent-test-notes.txt"))
        assertTrue(source.contains("firebaseAppDistributionRespondentGroups"))
    }

    @Test
    fun appCheckTokenSendFlag_controlsBackendHeaderAndStartupRefresh() {
        val appSource = read(resolve("app", "src", "main", "java", "com", "pcosina", "app", "PcosinaApp.kt"))
        val repositorySource = read(resolve("app", "src", "main", "java", "com", "pcosina", "app", "data", "repository", "MealPlanRepository.kt"))

        assertTrue(appSource.contains("DebugAppCheckProviderFactory"))
        assertTrue(appSource.contains("PlayIntegrityAppCheckProviderFactory"))
        assertTrue(appSource.contains("setTokenAutoRefreshEnabled(BuildConfig.PCOSINA_SEND_APP_CHECK)"))
        assertTrue(repositorySource.contains("if (BuildConfig.PCOSINA_SEND_APP_CHECK)"))
        assertTrue(repositorySource.contains("X-Firebase-AppCheck"))
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
