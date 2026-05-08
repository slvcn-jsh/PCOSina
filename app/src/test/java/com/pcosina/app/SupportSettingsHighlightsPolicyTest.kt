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

        assertTrue("Support should show the Figma support page headline.", support.contains("Support Page"))
        assertTrue("Support should keep the feedback CTA.", support.contains("Send feedback now"))
        assertTrue("Support should include the app directory section.", support.contains("App Directory"))
        assertTrue("Support should use the shared PCOSina avatar illustration.", support.contains("PcosinaAvatar"))
        assertTrue("Support should receive the selected avatar instead of hardcoding a character.", support.contains("avatarId: String"))
        assertTrue("Support should render the selected avatar.", support.contains("avatarId = avatarId"))
    }

    @Test
    fun progressScreen_surfacesWeeklyHighlightsFromCheckIns() {
        val progress = readMain("ui", "screens", "ProgressRefinedScreen.kt")

        assertTrue("Progress should include weekly highlights.", progress.contains("Weekly Highlights"))
        assertTrue("Weekly highlights should use check-in energy data.", progress.contains("averageEnergy"))
        assertTrue("Weekly highlights should reference savings.", progress.contains("Your Weekly Savings"))
        assertTrue("Weekly highlights should reference how the user is feeling.", progress.contains("How You're Feeling"))
        assertTrue("Weekly highlights should receive the selected avatar.", progress.contains("avatarId = profile.avatarId"))
        assertTrue("Weekly highlights should render the selected avatar.", progress.contains("avatarId = avatarId"))
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
