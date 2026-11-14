package com.pcosina.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.ui.components.ScreenArtworkAlignment
import com.pcosina.app.ui.components.SharedAvatarHeader
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7VisualPolishUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedAvatarHeader_handlesLongCopyInCompactAndRegularModes() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(320.dp)
                                .testTag(COMPACT_HEADER_TAG)
                        ) {
                            SharedAvatarHeader(
                                title = "Welcome, Alexandra Christine!",
                                subtitle = "Track meals, grocery spend, and progress without clipping important text.",
                                avatarId = "doctor_dog",
                                dateLabel = "September 27",
                                compact = true,
                                avatarAlignment = ScreenArtworkAlignment.HomeHeaderAvatar,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(REGULAR_HEADER_TAG)
                        ) {
                            SharedAvatarHeader(
                                title = "Progress and Check-ins",
                                subtitle = "Review meal adherence, macros, symptoms, and next-plan signals.",
                                avatarId = "cat",
                                dateLabel = "September 27",
                                compact = false,
                                avatarAlignment = ScreenArtworkAlignment.ProgressHeaderAvatar,
                            )
                        }
                    }
                }
            }
        }

        assertHeaderScreenshot(COMPACT_HEADER_TAG, "phase7_shared_header_compact")
        assertHeaderScreenshot(REGULAR_HEADER_TAG, "phase7_shared_header_regular")
    }

    private fun assertHeaderScreenshot(tag: String, snapshotName: String) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag(tag).assertCountEquals(1)
        val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        assertTrue("Header screenshot should have dimensions.", bitmap.width > 0 && bitmap.height > 0)
        assertTrue("Header should not collapse to the old clipped compact height.", bitmap.height >= 86)
        assertTrue("Header screenshot should contain visual variance.", hasVisualVariance(bitmap))
        saveCurrentSnapshot(bitmap, snapshotName)
    }

    private fun hasVisualVariance(bitmap: Bitmap): Boolean {
        val first = bitmap.getPixel(0, 0)
        val stepX = (bitmap.width / 32).coerceAtLeast(1)
        val stepY = (bitmap.height / 32).coerceAtLeast(1)
        var inspected = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) != first) return true
                inspected += 1
                if (inspected > 1024) return false
                x += stepX
            }
            y += stepY
        }
        return false
    }

    private fun saveCurrentSnapshot(bitmap: Bitmap, snapshotName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "visual_regression/current")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, "$snapshotName.png")
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private companion object {
        const val COMPACT_HEADER_TAG = "phase7_shared_header_compact_capture"
        const val REGULAR_HEADER_TAG = "phase7_shared_header_regular_capture"
    }
}
