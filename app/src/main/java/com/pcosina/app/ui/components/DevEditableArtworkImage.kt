package com.pcosina.app.ui.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pcosina.app.BuildConfig
import com.pcosina.app.data.model.ArtworkAlignmentConfig
import com.pcosina.app.data.repository.ArtworkAlignmentRepository
import java.util.Locale
import kotlinx.coroutines.launch

object ArtworkAlignmentKeys {
    const val LoginSnacksBackground = "login_snacks_background"
    const val LoginTermsSnacksBackground = "login_terms_snacks_background"
    const val LoginOwnershipWatermark = "login_ownership_watermark"
    const val LoginTermsOwnershipWatermark = "login_terms_ownership_watermark"
    const val SettingsHeroGear = "settings_hero_gear"
    const val SettingsProfileBackground = "settings_profile_background"
    const val SettingsProfileBackgroundBand = "settings_profile_background_band"
    const val HeaderAvatar = "meal_plan_header_avatar"
    const val SharedHeaderAvatar = HeaderAvatar
    const val HomeHeaderAvatar = HeaderAvatar
    const val MealPlanHeaderAvatar = HeaderAvatar
    const val GroceryHeaderAvatar = HeaderAvatar
    const val ProgressHeaderAvatar = HeaderAvatar
    const val SupportHeaderAvatar = HeaderAvatar
    const val NotificationsHeaderAvatar = HeaderAvatar
    const val ProgressHistoryCalendar = "progress_history_calendar"
    const val GroceryBudgetIllustration = "grocery_budget_illustration"
    const val GroceryProgressBackground = "grocery_progress_background"
    const val GroceryKitchenHubIllustration = "grocery_kitchen_hub_illustration"
}

object ArtworkAlignmentDefaults {
    fun forKey(key: String): ArtworkAlignmentConfig =
        when (key) {
            ArtworkAlignmentKeys.LoginSnacksBackground -> ArtworkAlignmentConfig(
                offsetXDp = -1.988174f,
                offsetYDp = -5.524216f,
                scale = 1.127192f,
            )
            ArtworkAlignmentKeys.LoginTermsSnacksBackground -> ArtworkAlignmentConfig(
                offsetXDp = 2.320084f,
                offsetYDp = -0.321716f,
                scale = 1.144044f,
            )
            ArtworkAlignmentKeys.LoginOwnershipWatermark -> ArtworkAlignmentConfig()
            ArtworkAlignmentKeys.LoginTermsOwnershipWatermark -> ArtworkAlignmentConfig()
            ArtworkAlignmentKeys.SettingsHeroGear -> ArtworkAlignmentConfig(
                offsetXDp = 46.426361f,
                offsetYDp = 0.409607f,
                scale = 1.0f,
            )
            ArtworkAlignmentKeys.SettingsProfileBackground -> ArtworkAlignmentConfig(
                offsetXDp = 9.021454f,
                offsetYDp = -19.446838f,
                scale = 1.105592f,
            )
            ArtworkAlignmentKeys.SettingsProfileBackgroundBand -> ArtworkAlignmentConfig()
            ArtworkAlignmentKeys.HeaderAvatar -> ArtworkAlignmentConfig(
                offsetXDp = 6.001877f,
                offsetYDp = 9.667343f,
                scale = 1.28f,
            )
            ArtworkAlignmentKeys.ProgressHistoryCalendar -> ArtworkAlignmentConfig(
                offsetXDp = 1.239166f,
                offsetYDp = 0.620605f,
                scale = 2.25f,
            )
            ArtworkAlignmentKeys.GroceryBudgetIllustration -> ArtworkAlignmentConfig()
            ArtworkAlignmentKeys.GroceryProgressBackground -> ArtworkAlignmentConfig(
                offsetXDp = -15.811935f,
                offsetYDp = -39.412964f,
                scale = 1.304993f,
            )
            ArtworkAlignmentKeys.GroceryKitchenHubIllustration -> ArtworkAlignmentConfig()
            else -> ArtworkAlignmentConfig.Default
        }
}

data class ArtworkAlignmentTarget(
    val key: String,
    val label: String,
)

@Composable
fun DevEditableArtworkImage(
    alignmentKey: String,
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    alpha: Float = 1f,
    maxScale: Float = ArtworkAlignmentConfig.MaxScale,
    artworkScaleMultiplier: Float = 1f,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    externalEditing: Boolean? = null,
    onExternalEditingChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember(context) { ArtworkAlignmentRepository(context) }
    val defaultConfig = remember(alignmentKey) { ArtworkAlignmentDefaults.forKey(alignmentKey) }
    val rawSavedConfig by repository.alignmentFlow(
        key = alignmentKey,
        defaultConfig = defaultConfig,
    ).collectAsState(
        initial = defaultConfig
    )
    val savedConfig = remember(rawSavedConfig, maxScale) {
        rawSavedConfig.clamped(maxScale = maxScale)
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var internalEditing by remember(alignmentKey) { mutableStateOf(false) }
    var draftConfig by remember(alignmentKey) { mutableStateOf(savedConfig) }
    val editing = externalEditing ?: internalEditing
    val activeConfig = if (editing) draftConfig else savedConfig
    fun setEditing(value: Boolean) {
        if (onExternalEditingChange != null) {
            onExternalEditingChange(value)
        } else {
            internalEditing = value
        }
    }

    LaunchedEffect(savedConfig, editing) {
        if (!editing) {
            draftConfig = savedConfig
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            draftConfig = savedConfig
        }
    }

    val editorModifier = if (BuildConfig.DEBUG) {
        Modifier.pointerInput(editing, savedConfig, density) {
            if (editing) {
                detectTransformGestures { _, pan, zoom, _ ->
                    draftConfig = draftConfig
                        .translatedBy(pan, density.density)
                        .scaledBy(zoom)
                        .clamped(maxScale = maxScale)
                }
            } else {
                detectTapGestures(
                    onLongPress = {
                        draftConfig = savedConfig
                        setEditing(true)
                    }
                )
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = activeConfig.offsetXDp.dp.toPx()
                translationY = activeConfig.offsetYDp.dp.toPx()
                scaleX = activeConfig.scale * artworkScaleMultiplier
                scaleY = activeConfig.scale * artworkScaleMultiplier
                this.transformOrigin = transformOrigin
            }
            .then(editorModifier)
            .then(
                if (BuildConfig.DEBUG && editing) {
                    Modifier.border(2.dp, Color(0xFFFF2E63), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alignment = alignment,
            alpha = alpha,
        )
    }

    if (BuildConfig.DEBUG && editing) {
        ArtworkAlignmentEditorDialog(
            keyLabel = alignmentKey,
            config = draftConfig,
            onTransform = { pan, zoom ->
                draftConfig = draftConfig
                    .translatedBy(pan, density.density)
                    .scaledBy(zoom)
                    .clamped(maxScale = maxScale)
            },
            onCancel = {
                draftConfig = savedConfig
                setEditing(false)
            },
            onReset = {
                draftConfig = defaultConfig
                scope.launch {
                    repository.resetAlignment(alignmentKey)
                    Log.i("ArtworkAlignment", "Reset $alignmentKey")
                    setEditing(false)
                }
            },
            onSave = {
                val next = draftConfig.clamped(maxScale = maxScale)
                draftConfig = next
                scope.launch {
                    repository.saveAlignment(alignmentKey, next)
                    Log.i(
                        "ArtworkAlignment",
                        "Saved $alignmentKey " +
                            "x=${next.offsetXDp.format1()}dp " +
                            "y=${next.offsetYDp.format1()}dp " +
                            "scale=${next.scale.format2()}"
                    )
                    setEditing(false)
                }
            },
        )
    }
}

@Composable
fun DevArtworkAlignmentHotspot(
    targets: List<ArtworkAlignmentTarget>,
    onEditTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.DEBUG || targets.isEmpty()) return

    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.pointerInput(targets) {
            detectTapGestures(onLongPress = { menuOpen = true })
        }
    )

    if (menuOpen) {
        Popup(
            alignment = Alignment.TopEnd,
            properties = PopupProperties(focusable = true),
        ) {
            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .widthIn(min = 210.dp, max = 280.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF26171D).copy(alpha = 0.96f),
                contentColor = Color.White,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    targets.forEach { target ->
                        TextButton(
                            onClick = {
                                menuOpen = false
                                onEditTarget(target.key)
                            }
                        ) {
                            Text(target.label)
                        }
                    }
                    TextButton(onClick = { menuOpen = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkAlignmentEditorDialog(
    keyLabel: String,
    config: ArtworkAlignmentConfig,
    onTransform: (Offset, Float) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onTransform(pan, zoom)
                    }
                },
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .widthIn(min = 230.dp, max = 300.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF26171D).copy(alpha = 0.96f),
                contentColor = Color.White,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = keyLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "x ${config.offsetXDp.format1()}dp  y ${config.offsetYDp.format1()}dp  scale ${config.scale.format2()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        OutlinedButton(onClick = onReset) {
                            Text("Reset")
                        }
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7188)),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

private fun ArtworkAlignmentConfig.translatedBy(
    pan: Offset,
    density: Float,
): ArtworkAlignmentConfig {
    val safeDensity = density.takeIf { it > 0f } ?: 1f
    return copy(
        offsetXDp = offsetXDp + pan.x / safeDensity,
        offsetYDp = offsetYDp + pan.y / safeDensity,
    )
}

private fun ArtworkAlignmentConfig.scaledBy(scaleChange: Float): ArtworkAlignmentConfig =
    copy(scale = scale * scaleChange)

private fun Float.format1(): String = String.format(Locale.ENGLISH, "%.1f", this)

private fun Float.format2(): String = String.format(Locale.ENGLISH, "%.2f", this)
