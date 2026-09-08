package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportSettingsHighlightsPolicyTest {

    @Test
    fun settingsAvatarChoice_isPersistedThroughUserProfile() {
        val profile = readMain("data", "model", "UserProfile.kt")
        val userViewModel = readMain("ui", "UserViewModel.kt")
        val preferences = readMain("data", "repository", "UserPreferencesRepository.kt")
        val settings = readMain("ui", "screens", "SettingsScreen.kt")

        assertTrue("UserProfile should carry the selected avatar.", profile.contains("val avatarId: String"))
        assertTrue("UserViewModel should expose an avatar update action.", userViewModel.contains("fun updateAvatar("))
        assertTrue("Avatar should be stored locally.", preferences.contains("preferences[Keys.avatar(userId)] = profile.avatarId"))
        assertTrue("Avatar should sync with the profile payload.", preferences.contains("\"avatarId\" to profile.avatarId"))
        assertTrue("Settings should show the avatar picker.", settings.contains("Choose your avatar"))
    }

    @Test
    fun avatarPicker_usesRealImportedAvatarAssets() {
        val avatarComponent = readMain("ui", "components", "PcosinaAvatar.kt")

        assertTrue("Avatar component should render imported drawable assets.", avatarComponent.contains("painterResource(id = option.drawableRes)"))
        assertTrue("Doctor dog asset should be mapped.", avatarComponent.contains("R.drawable.avatar_doctor_dog"))
        assertTrue("Cat asset should be mapped.", avatarComponent.contains("R.drawable.avatar_cat"))
        assertTrue("Chick asset should be mapped.", avatarComponent.contains("R.drawable.avatar_chick"))
        assertTrue("Frog asset should be mapped.", avatarComponent.contains("R.drawable.avatar_frog"))
        assertTrue("Koala asset should be mapped.", avatarComponent.contains("R.drawable.avatar_koala"))
        assertTrue("Pug asset should be mapped.", avatarComponent.contains("R.drawable.avatar_pug"))
    }

    @Test
    fun supportScreen_matchesSupportHubDirection() {
        val support = readMain("ui", "screens", "CommunityScreen.kt")

        assertTrue("Support should use the shared Support header without a duplicate page label.", support.contains("title = \"Support\""))
        assertTrue("Support should not show a redundant Support Page label below the header.", !support.contains("Support Page"))
        assertTrue("Support should keep the feedback CTA.", support.contains("Send feedback now"))
        assertTrue("Support should not include the removed app directory section.", !support.contains("App Directory"))
        assertTrue("Support should not include the removed open-plan shortcut.", !support.contains("Open this week's plan"))
        assertTrue("Support should not keep inactive video placeholder cards.", !support.contains("SupportVideoCard"))
        assertTrue("Support should use the shared avatar header chrome.", support.contains("SharedAvatarHeader("))
        assertTrue("Support should use the support avatar artwork alignment token.", support.contains("ScreenArtworkAlignment.SupportHeaderAvatar"))
        assertTrue("Support should receive the selected avatar instead of hardcoding a character.", support.contains("avatarId: String"))
        assertTrue("Support should render the selected avatar.", support.contains("avatarId = avatarId"))
    }

    @Test
    fun progressScreen_surfacesWeeklyHighlightsFromCheckIns() {
        val progress = readMain("ui", "screens", "ProgressRefinedScreen.kt")

        assertTrue("Progress should include weekly highlights.", progress.contains("Weekly Highlights"))
        assertTrue("Weekly highlights should still use logged check-in data.", progress.contains("mostFollowedMealType(logs, weekStart)"))
        assertTrue("Weekly highlights should reference budget status.", progress.contains("Weekly Savings") && progress.contains("Estimated Remaining Budget"))
        assertTrue("Weekly highlights should show the replacement meal-following insight.", progress.contains("Most Followed Meal Type"))
        assertTrue("Weekly highlights should remove the stale feeling highlight.", !progress.contains("How You're Feeling"))
        assertTrue("Weekly highlights should no longer render the old avatar badge.", !progress.contains("PcosinaAvatarBadge"))
    }

    private fun readMain(vararg parts: String): String {
        val path = resolve("app", "src", "main", "java", "com", "pcosina", "app", *parts)
        return String(Files.readAllBytes(path))
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
