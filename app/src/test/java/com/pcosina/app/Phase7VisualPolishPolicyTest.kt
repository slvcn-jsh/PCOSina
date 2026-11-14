package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7VisualPolishPolicyTest {

    @Test
    fun sharedAvatarHeader_growsWithContentAndPreservesTransparentAvatarAlpha() {
        val source = readMainSource("ui", "components", "PcosinaSharedChrome.kt")

        assertTrue(
            "SharedAvatarHeader should use min-height sizing so longer title/subtitle/date text can grow instead of clipping.",
            source.contains(".heightIn(min = headerMinHeight)")
        )
        assertFalse(
            "SharedAvatarHeader should not keep the old fixed 76/84dp height.",
            source.contains(".height(if (compact) 76.dp else 84.dp)")
        )
        assertTrue(
            "SharedAvatarHeader should render the avatar PNG directly so transparent areas stay transparent.",
            source.contains("PcosinaAvatar(")
        )
        assertFalse(
            "SharedAvatarHeader should not force the avatar into the circular badge surface.",
            source.contains("PcosinaAvatarBadge(")
        )
        assertTrue(
            "SharedAvatarHeader sizing should be centralized with the other visual polish tokens.",
            source.contains("ScreenArtworkSizing.AvatarHeaderCompactMinHeight") &&
                source.contains("ScreenArtworkSizing.AvatarHeaderRegularMinHeight")
        )
    }

    @Test
    fun dashboardDailyTips_replacesGoalsCard() {
        val source = readMainSource("ui", "screens", "DashboardRefinedScreen.kt")

        assertTrue("Home should keep the expanded Daily Tips card.", source.contains("title = \"Daily Tips\""))
        assertFalse("Home should not keep the removed Your Goals card.", source.contains("Your Goals"))
        assertFalse("Home should not keep the removed home goal chip helper.", source.contains("HomeGoalChip("))
    }

    @Test
    fun screenFamilies_useCentralArtworkAlignmentAndSizingTokens() {
        val tokens = readMainSource("ui", "components", "ScreenArtworkTokens.kt")
        val dashboard = readMainSource("ui", "screens", "DashboardRefinedScreen.kt")
        val grocery = readMainSource("ui", "screens", "GroceryRefinedScreen.kt")
        val progress = readMainSource("ui", "screens", "ProgressRefinedScreen.kt")
        val support = readMainSource("ui", "screens", "CommunityScreen.kt")

        listOf(
            "HomeHeaderAvatar",
            "GroceryHeaderAvatar",
            "ProgressHeaderAvatar",
            "SupportHeaderAvatar"
        ).forEach { token ->
            assertTrue("Missing artwork alignment token: $token", tokens.contains(token))
        }

        assertTrue(dashboard.contains("avatarAlignment = ScreenArtworkAlignment.HomeHeaderAvatar"))
        assertTrue(grocery.contains("avatarAlignment = ScreenArtworkAlignment.GroceryHeaderAvatar"))
        assertTrue(grocery.contains("alignment = ScreenArtworkAlignment.GroceryProgressBackground"))
        assertTrue(progress.contains("avatarAlignment = ScreenArtworkAlignment.ProgressHeaderAvatar"))
        assertFalse(progress.contains("alignment = ScreenArtworkAlignment.ProgressReviewIllustration"))
        assertTrue(support.contains("avatarAlignment = ScreenArtworkAlignment.SupportHeaderAvatar"))
        assertFalse(support.contains("SupportFreshStartCard("))
    }

    @Test
    fun avatarPngs_keepAlphaCapablePngFormatAndNonEmptyArtworkFiles() {
        val avatarDir = resolve("app", "src", "main", "res", "drawable-nodpi")
        val expected = listOf(
            "avatar_doctor_dog.png",
            "avatar_cat.png",
            "avatar_chick.png",
            "avatar_frog.png",
            "avatar_koala.png",
            "avatar_pug.png"
        )

        expected.forEach { fileName ->
            val path = avatarDir.resolve(fileName)
            assertTrue("Missing avatar asset: $fileName", Files.exists(path))
            val bytes = Files.readAllBytes(path)
            assertTrue("Avatar should not be placeholder-sized: $fileName", bytes.size > 10_000)
            assertTrue("Avatar should be a PNG: $fileName", bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE))
            val colorType = bytes[25].toInt() and 0xFF
            assertTrue(
                "Avatar PNG should keep an alpha-capable color type: $fileName",
                colorType == PNG_COLOR_TYPE_TRUECOLOR_ALPHA || colorType == PNG_COLOR_TYPE_GRAYSCALE_ALPHA
            )
        }
    }

    private fun readMainSource(vararg parts: String): String =
        String(
            Files.readAllBytes(
                resolve(
                    "app",
                    "src",
                    "main",
                    "java",
                    "com",
                    "pcosina",
                    "app",
                    *parts
                )
            )
        )

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        const val PNG_COLOR_TYPE_GRAYSCALE_ALPHA = 4
        const val PNG_COLOR_TYPE_TRUECOLOR_ALPHA = 6
    }
}
