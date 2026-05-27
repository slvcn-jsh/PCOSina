@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pcosina.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.ExpandableSection
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.GuidedJourneyCard
import com.pcosina.app.ui.components.GuidedJourneyStep
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.components.MacroProgressBar
import com.pcosina.app.ui.components.MealCheckInDialog
import com.pcosina.app.ui.components.StatCard
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.ui.components.SyncStatusChip
import com.pcosina.app.ui.components.TokenizedFilterChip
import com.pcosina.app.ui.screens.BottomActionRow
import com.pcosina.app.ui.screens.OnboardingProgress
import com.pcosina.app.ui.screens.RecipeImpactSummary
import com.pcosina.app.ui.screens.RecipeImpactSummarySection
import com.pcosina.app.ui.screens.ProgressJumpToTodayAction
import com.pcosina.app.ui.screens.ProgressLoggingPolicyLearnMoreChip
import com.pcosina.app.ui.screens.SettingsActionItem
import com.pcosina.app.ui.screens.SettingsItem
import com.pcosina.app.ui.screens.SettingsSection
import com.pcosina.app.ui.screens.StepOneIdentity
import com.pcosina.app.ui.screens.StepThreeDiet
import com.pcosina.app.ui.theme.PCOSINATheme
import com.pcosina.app.ui.theme.PcosinaSuccess

@Preview(
    name = "Dashboard Status Compact",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Dashboard Status Dark",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun DashboardStatusHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Today at a glance",
            subtitle = "Offline-safe planning cues, sync status, and the next best action.",
            trailing = {
                SyncStatusChip(state = FeedbackActionState.Loading)
            },
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Loading,
                message = "Refreshing your meal plan from local data while sync waits for a connection.",
                actionLabel = "Retry",
            ),
            onAction = {},
        )
        StatusCenterCard(
            queuedActionsLabel = "2 updates queued for sync",
            syncLabel = "Last full sync: Apr 19, 10:10 AM",
            planRangeLabel = "Plan active: Apr 19 to Apr 25",
            nextReminderLabel = "Next reminder: Lunch check-in at 12:00 PM",
        )
    }
}

@Preview(
    name = "Dashboard Long Text",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
)
@Composable
fun DashboardLongTextHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Today at a glance with a much longer title than the compact header can comfortably hold",
            subtitle = "This preview intentionally uses a verbose offline status summary so you can inspect truncation, line wrapping, and whether the most important action still stays visually obvious on compact widths.",
            trailing = {
                SyncStatusChip(state = FeedbackActionState.Error)
            },
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "Sync is delayed because the last three attempts timed out while the app was still preserving your local edits, grocery changes, and reminder acknowledgements for the next safe retry window.",
                actionLabel = "Retry now",
            ),
            onAction = {},
        )
        StatusCenterCard(
            queuedActionsLabel = "7 updates still waiting, including grocery substitutions, one weekly plan refresh, and two progress check-ins.",
            syncLabel = "Last successful sync happened before your latest pantry edits, reminder changes, and progress notes were saved locally.",
            planRangeLabel = "Plan active: Apr 19 to Apr 25 with substitutions still pending confirmation after the next reconnect.",
            nextReminderLabel = "Next reminder: A longer lunch check-in label that forces the card to prove its truncation behavior.",
        )
    }
}

@Preview(
    name = "Guided Journey Compact",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Guided Journey Large Font",
    widthDp = 360,
    heightDp = 900,
    fontScale = 1.3f,
    showBackground = true,
)
@Composable
fun GuidedJourneyHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Build your first plan",
            subtitle = "The guided journey reduces friction and keeps the next screen obvious.",
        )
        GuidedJourneyCard(
            step = GuidedJourneyStep(
                stepIndex = 2,
                totalSteps = 6,
                title = "Set your planning goal",
                rationale = "Choosing the goal early lets the planner explain why calories, fiber, and sugar targets change.",
                route = "goal",
                requiresInternet = false,
            ),
            onContinue = {},
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TokenizedFilterChip(
                selected = true,
                text = "Weight Loss",
                onClick = {},
                labelMaxWidth = 120.dp,
            )
            TokenizedFilterChip(
                selected = false,
                text = "Symptom Management",
                onClick = {},
                labelMaxWidth = 140.dp,
            )
            TokenizedFilterChip(
                selected = false,
                text = "30 min max prep",
                onClick = {},
                labelMaxWidth = 132.dp,
            )
        }
    }
}

@Preview(
    name = "Login Compact",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Login Dark",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun LoginHostPreview() {
    PreviewSurface {
        val colorScheme = MaterialTheme.colorScheme

        GradientHeader(
            title = "Welcome back",
            subtitle = "Open your saved plan, grocery list, and progress from local data first.",
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "First win in ~60–90 seconds",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "Sign in -> Complete your profile -> Choose your goal -> Build your first week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "Step 1 of 6: Sign in",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = "strongpassword",
            onValueChange = {},
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {}) {
                Text("Forgot password?")
            }
        }
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Email sign-in stays available while Google sign-in is still being finalized on this build.",
            ),
        )
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Text("Sign in", fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Create an account")
            }
        }
    }
}

@Preview(
    name = "Login Error",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
)
@Composable
fun LoginErrorHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Welcome back",
            subtitle = "Review the failed sign-in state before this copy reaches a real device.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "We could not sign you in because the device is offline and your last session expired. Use a stable connection to refresh credentials, or keep working in saved local screens that do not require account revalidation.",
                actionLabel = "Try again",
            ),
            onAction = {},
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = "wrong-password",
            onValueChange = {},
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                Text("Password reset is available, but you still need connectivity to complete the flow.")
            },
        )
        LoadingActionButton(
            state = FeedbackActionState.Error,
            idleLabel = "Sign in",
            loadingLabel = "Signing in",
            successLabel = "Signed in",
            errorLabel = "Try again",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Forgot password?")
            }
        }
    }
}

@Preview(
    name = "Sign Up",
    widthDp = 360,
    heightDp = 980,
    showBackground = true,
)
@Preview(
    name = "Sign Up Dark",
    widthDp = 360,
    heightDp = 980,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SignUpHostPreview() {
    val colorScheme = MaterialTheme.colorScheme

    PreviewSurface {
        Text(
            text = "PCOSINA",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            color = colorScheme.secondary,
        )
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground,
        )
        Text(
            text = "Your journey to balanced health starts here",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = "Stronger@123",
            onValueChange = {},
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = "Stronger@123",
            onValueChange = {},
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = "Sign Up",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Already have an account? Login", color = colorScheme.secondary)
            }
        }
    }
}

@Preview(
    name = "Sign Up Loading",
    widthDp = 360,
    heightDp = 980,
    showBackground = true,
)
@Composable
fun SignUpLoadingHostPreview() {
    val colorScheme = MaterialTheme.colorScheme

    PreviewSurface {
        Text(
            text = "PCOSINA",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            color = colorScheme.secondary,
        )
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground,
        )
        Text(
            text = "Your journey to balanced health starts here",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = "Stronger@123",
            onValueChange = {},
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = "Stronger@123",
            onValueChange = {},
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        LoadingActionButton(
            state = FeedbackActionState.Loading,
            idleLabel = "Sign Up",
            loadingLabel = "Creating account",
            successLabel = "Verification sent",
            errorLabel = "Retry",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Keep this screen open while your account is created and verification is prepared.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Already have an account? Login", color = colorScheme.secondary)
            }
        }
    }
}

@Preview(
    name = "Sign Up Email Format Error",
    widthDp = 360,
    heightDp = 980,
    showBackground = true,
)
@Composable
fun SignUpEmailFormatErrorHostPreview() {
    val colorScheme = MaterialTheme.colorScheme

    PreviewSurface {
        Text(
            text = "PCOSINA",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            color = colorScheme.secondary,
        )
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground,
        )
        Text(
            text = "Your journey to balanced health starts here",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = "salva@pcosina",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = "Stronger@123",
            onValueChange = {},
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = "Stronger@123",
            onValueChange = {},
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        Text(
            text = "Enter a valid email",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        LoadingActionButton(
            state = FeedbackActionState.Error,
            idleLabel = "Sign Up",
            loadingLabel = "Creating account",
            successLabel = "Verification sent",
            errorLabel = "Fix and retry",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Already have an account? Login", color = colorScheme.secondary)
            }
        }
    }
}

@Preview(
    name = "Sign Up Validation Error",
    widthDp = 360,
    heightDp = 980,
    showBackground = true,
)
@Composable
fun SignUpValidationErrorHostPreview() {
    val colorScheme = MaterialTheme.colorScheme

    PreviewSurface {
        Text(
            text = "PCOSINA",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            color = colorScheme.secondary,
        )
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground,
        )
        Text(
            text = "Your journey to balanced health starts here",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = "strongpass",
            onValueChange = {},
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                Text("Password must include at least one special character")
            },
        )
        OutlinedTextField(
            value = "strongpass",
            onValueChange = {},
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        Text(
            text = "Password must include at least one special character",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        LoadingActionButton(
            state = FeedbackActionState.Error,
            idleLabel = "Sign Up",
            loadingLabel = "Creating account",
            successLabel = "Verification sent",
            errorLabel = "Fix and retry",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Already have an account? Login", color = colorScheme.secondary)
            }
        }
    }
}

@Preview(
    name = "Sign Up Verification Sent",
    widthDp = 360,
    heightDp = 980,
    showBackground = true,
)
@Composable
fun SignUpVerificationSentHostPreview() {
    val colorScheme = MaterialTheme.colorScheme

    PreviewSurface {
        Text(
            text = "PCOSINA",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            color = colorScheme.secondary,
        )
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground,
        )
        Text(
            text = "Your journey to balanced health starts here",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Verification email sent. Please check your inbox and verify before logging in.",
            ),
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        LoadingActionButton(
            state = FeedbackActionState.Success,
            idleLabel = "Sign Up",
            loadingLabel = "Creating account",
            successLabel = "Verification sent",
            errorLabel = "Retry",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Go to Login", color = colorScheme.primary)
            }
        }
    }
}

@Preview(
    name = "Account Recovery Reset Sent",
    widthDp = 360,
    heightDp = 940,
    showBackground = true,
)
@Composable
fun AccountRecoveryResetSentHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Recover your account",
            subtitle = "Review the password-reset success path and the next user action.",
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Password reset email sent. Check your inbox.",
            ),
        )
        Text(
            text = "Open your email app, use the reset link, then return here to sign in with the new password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {}) {
                Text("Back to Login")
            }
        }
    }
}

@Preview(
    name = "Account Recovery Verification",
    widthDp = 360,
    heightDp = 980,
    showBackground = true,
)
@Composable
fun AccountRecoveryVerificationHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Verify your account",
            subtitle = "Review the resend-verification recovery path before it reaches a real device.",
        )
        OutlinedTextField(
            value = "salva@pcosina.app",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            value = "Strong@123",
            onValueChange = {},
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Verification email sent. Please check your inbox.",
            ),
        )
        Text(
            text = "If the email does not arrive, confirm spam folders and make sure the password matches the account before trying again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = {}) {
                Text("Back to Login")
            }
        }
    }
}

@Preview(
    name = "Profile Identity Compact",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Profile Identity Large Font",
    widthDp = 360,
    heightDp = 900,
    fontScale = 1.2f,
    showBackground = true,
)
@Composable
fun ProfileIdentityHostPreview() {
    PreviewSurface {
        val primaryColor = MaterialTheme.colorScheme.primary

        GradientHeader(
            title = "Complete your profile",
            subtitle = "These details personalize targets and keep solver inputs valid.",
        )
        OnboardingProgress(currentStep = 1, color = primaryColor)
        StepOneIdentity(
            name = "Salva",
            onName = {},
            age = "27",
            onAge = {},
            weight = "68",
            onWeight = {},
            weightUnit = UnitConverter.WEIGHT_KG,
            onWeightUnit = {},
            targetWeight = "64",
            onTargetWeight = {},
            targetDate = "2026-08-01",
            onTargetDate = {},
            heightUnit = UnitConverter.HEIGHT_CM,
            onHeightUnit = {},
            heightCm = "160",
            onHeightCm = {},
            heightFt = "",
            onHeightFt = {},
            heightIn = "",
            onHeightIn = {},
            activity = "Lightly Active",
            onActivity = {},
            color = primaryColor,
            showName = true,
        )
        BottomActionRow(
            currentStep = 1,
            primaryLabel = "Next step",
            primaryColor = primaryColor,
            isNextEnabled = true,
            onBack = {},
            onNext = {},
        )
    }
}

@Preview(
    name = "Profile Planning Compact",
    widthDp = 360,
    heightDp = 1200,
    showBackground = true,
)
@Preview(
    name = "Profile Planning Dark",
    widthDp = 360,
    heightDp = 1200,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ProfilePlanningHostPreview() {
    PreviewSurface {
        val primaryColor = MaterialTheme.colorScheme.primary

        GradientHeader(
            title = "Lock in planning rules",
            subtitle = "Budget, pantry, and hard food constraints shape the final week.",
        )
        OnboardingProgress(currentStep = 3, color = primaryColor)
        StepThreeDiet(
            r1 = true,
            onR1 = {},
            r2 = false,
            onR2 = {},
            r3 = true,
            onR3 = {},
            r4 = true,
            onR4 = {},
            r5 = false,
            onR5 = {},
            budget = "2500",
            onBudget = {},
            maxCookingTime = "35",
            onMaxCookingTime = {},
            varietyPreference = "Balanced",
            onVarietyPreference = {},
            planningPriority = "Balanced",
            onPlanningPriority = {},
            pantryText = "eggs, monggo, chicken breast, pechay",
            onPantryText = {},
            allergiesText = "peanut",
            onAllergiesText = {},
            color = primaryColor,
        )
        BottomActionRow(
            currentStep = 3,
            primaryLabel = "Save profile",
            primaryColor = primaryColor,
            isNextEnabled = true,
            onBack = {},
            onNext = {},
        )
    }
}

@Preview(
    name = "Meal Plan Compact",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Preview(
    name = "Meal Plan Large Font",
    widthDp = 360,
    heightDp = 960,
    fontScale = 1.2f,
    showBackground = true,
)
@Composable
fun MealPlanHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "This week's meal plan",
            subtitle = "Balanced around budget, pantry, and variety without breaking hard rules.",
            trailing = {
                SyncStatusChip(state = FeedbackActionState.Success)
            },
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Week generated from local data. Sync can catch up later.",
            ),
        )
        StatusCenterCard(
            queuedActionsLabel = "Groceries ready for offline review",
            syncLabel = "Last sync: Apr 18, 8:42 PM",
            planRangeLabel = "Week of Apr 21 to Apr 27",
            nextReminderLabel = "Generate next week on Sunday night",
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TokenizedFilterChip(
                selected = true,
                text = "Low GI focus",
                onClick = {},
                labelMaxWidth = 112.dp,
            )
            TokenizedFilterChip(
                selected = true,
                text = "Pantry-first",
                onClick = {},
                labelMaxWidth = 110.dp,
            )
            TokenizedFilterChip(
                selected = false,
                text = "35 min max prep",
                onClick = {},
                labelMaxWidth = 132.dp,
            )
        }
        LoadingActionButton(
            state = FeedbackActionState.Idle,
            idleLabel = "Generate weekly plan",
            loadingLabel = "Building your week",
            successLabel = "Plan ready",
            errorLabel = "Retry generation",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        ExpandableSection(
            title = "Why this week fits",
            subtitle = "Constraint and nutrition summary",
            defaultExpanded = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroProgressBar(
                    label = "Fiber target",
                    progress = 0.88f,
                    valueText = "28 / 32 g",
                )
                MacroProgressBar(
                    label = "Protein target",
                    progress = 0.81f,
                    valueText = "93 / 115 g",
                )
                MacroProgressBar(
                    label = "Added sugar guardrail",
                    progress = 0.36f,
                    valueText = "18 / 50 g",
                    barColor = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "Budget headroom",
                value = "P 410",
                subtitle = "Under your weekly cap",
                footer = "Balanced priority",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Variety score",
                value = "High",
                subtitle = "5 unique dinners",
                footer = "Max prep 35 min",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(
    name = "Meal Plan No Safe Plan",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Composable
fun MealPlanNoSafePlanHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "This week's meal plan",
            subtitle = "Review the no-safe-plan state so the fallback feels useful instead of dead-ended.",
            trailing = {
                SyncStatusChip(state = FeedbackActionState.Error)
            },
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "No safe plan matched your current hard rules. The planner kept allergies, budget, pantry feasibility, and cooking-time ceilings intact instead of forcing an unsafe week.",
                actionLabel = "Adjust inputs",
            ),
            onAction = {},
        )
        PreviewNarrativeCard(
            title = "What blocked the plan",
            body = "Your current budget, peanut allergy, pantry-only preference, and 20-minute prep ceiling left too few meals to build a full week. This is the state where the UX needs to teach the next best move without sounding like a crash.",
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TokenizedFilterChip(
                selected = true,
                text = "Budget P1,200",
                onClick = {},
                labelMaxWidth = 110.dp,
            )
            TokenizedFilterChip(
                selected = true,
                text = "Peanut allergy",
                onClick = {},
                labelMaxWidth = 118.dp,
            )
            TokenizedFilterChip(
                selected = true,
                text = "20 min max prep",
                onClick = {},
                labelMaxWidth = 130.dp,
            )
            TokenizedFilterChip(
                selected = true,
                text = "Pantry-only bias",
                onClick = {},
                labelMaxWidth = 128.dp,
            )
        }
        ExpandableSection(
            title = "Suggested fixes",
            subtitle = "Keep hard constraints honest, but lower friction",
            defaultExpanded = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Increase the weekly budget, relax prep time to 35 minutes, or add a few staple proteins and vegetables to pantry inputs before trying again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "The planner should make these tradeoffs legible instead of leaving the user to guess what changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LoadingActionButton(
            state = FeedbackActionState.Error,
            idleLabel = "Generate weekly plan",
            loadingLabel = "Building your week",
            successLabel = "Plan ready",
            errorLabel = "Try again",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(
    name = "Grocery Empty",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Composable
fun GroceryEmptyHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review the first-run list state before a user generates their first week.",
        )
        PreviewNarrativeCard(
            title = "What to do next",
            body = "Your grocery list is filled from your current plan automatically. Generate your first weekly plan to unlock categories, estimates, and progress-linked shopping flows.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Loading,
                message = "No grocery items yet. Generate a weekly plan to populate your list automatically.",
                actionLabel = "Open plan",
            ),
            onAction = {},
        )
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Search ingredients") },
            placeholder = { Text("Try: egg, spinach, fish") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Showing 0 of 0 items • Need to buy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "No grocery items yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Generate your first weekly plan to unlock your grocery list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Generate Weekly Plan")
                }
            }
        }
    }
}

@Preview(
    name = "Grocery Search Empty",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Composable
fun GrocerySearchEmptyHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review the no-results state for search and filter friction.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Showing local grocery items from your current plan.",
            ),
        )
        OutlinedTextField(
            value = "dragonfruit powder",
            onValueChange = {},
            label = { Text("Search ingredients") },
            placeholder = { Text("Try: egg, spinach, fish") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Showing 0 of 14 items • Need to buy • Produce",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TokenizedFilterChip(
                selected = true,
                text = "Need to buy",
                onClick = {},
                labelMaxWidth = 110.dp,
            )
            TokenizedFilterChip(
                selected = false,
                text = "Completed",
                onClick = {},
                labelMaxWidth = 100.dp,
            )
            TokenizedFilterChip(
                selected = true,
                text = "Produce",
                onClick = {},
                labelMaxWidth = 88.dp,
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "No results for \"dragonfruit powder\"",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Try a broader keyword or clear search to browse all categories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Clear Search")
                }
            }
        }
    }
}

@Preview(
    name = "Recipe Loading",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Composable
fun RecipeLoadingHostPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Recipe Details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Loading recipe",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "Getting ingredients and steps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { PreviewSkeletonTile() }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                PreviewSkeletonBar(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(8.dp),
                )
            }
        }
    }
}

@Preview(
    name = "Recipe Error",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Composable
fun RecipeErrorHostPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Recipe Details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Couldn’t load recipe",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "We lost connection while fetching ingredients, cooking steps, and meal impact details for this recipe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Check connection and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Retry")
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Preview(
    name = "Recipe Success",
    widthDp = 360,
    heightDp = 1400,
    showBackground = true,
)
@Composable
fun RecipeSuccessHostPreview() {
    PreviewSurface {
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Logged Lunch. Today progress is now 2/3 meals.",
            ),
        )
        RecipeImpactSummarySection(
            summary = RecipeImpactSummary(
                headline = "Nice progress. Today 2/3 meals • 980/1450 kcal.",
                detailLine = "74/105 g protein • 21/30 g fiber",
                nextSuggestion = "Next: Dinner • Garlic Tofu Bowl, then add a short reflection for today.",
                nextRoute = "recipe/next-meal",
                nextCtaLabel = "Open Next Meal",
            ),
            detailsExpanded = true,
            onToggleDetails = {},
            onNavigateToRoute = {},
        )
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = false,
        ) {
            Text("Marked as Eaten Today", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Add to Grocery List")
        }
        PreviewRecipeHeroHeader(
            title = "Ginataang Gulay with Tofu",
            emoji = "\uD83E\uDD57",
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "LUNCH",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Ginataang Gulay with Tofu",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "\u23F1 25 min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Primary-user plan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Ingredients and grocery totals stay scoped to the primary-user plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Why it fits this week",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\u2022 Uses pantry-first tofu, pechay, and squash before they age out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\u2022 Keeps prep under 30 minutes while supporting fiber and protein targets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Nutrition summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Selected serving: 370 kcal • 24g protein",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PreviewRecipeMetric("Calories", "370", MaterialTheme.colorScheme.onSurface)
                    PreviewRecipeMetric("Protein", "24g", MaterialTheme.colorScheme.primary)
                    PreviewRecipeMetric("Carbs", "31g", MaterialTheme.colorScheme.primary)
                    PreviewRecipeMetric("Fiber", "9g", MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Ingredients",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PreviewIngredientRow("Tofu", "3 blocks")
                    PreviewIngredientRow("Pechay", "2 bunches")
                    PreviewIngredientRow("Squash", "600 g")
                    PreviewIngredientRow("Coconut milk", "3 cups")
                }
            }
            Text(
                text = "Ready to shop? Use the fixed Add button above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Cooking Steps",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            PreviewInstructionRow(
                step = 1,
                text = "Saute garlic and onion, then add squash and a splash of water until slightly tender.",
            )
            PreviewInstructionRow(
                step = 2,
                text = "Add coconut milk and tofu, simmer gently, then fold in pechay at the end to keep texture.",
            )
        }
    }
}

@Preview(
    name = "Recipe Locked Logging",
    widthDp = 360,
    heightDp = 1100,
    showBackground = true,
)
@Composable
fun RecipeLockedLoggingHostPreview() {
    PreviewSurface {
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "No change: logging is locked for this date.",
            ),
        )
        RecipeImpactSummarySection(
            summary = RecipeImpactSummary(
                headline = "Past-day logging is locked. Log meals on the same day to keep insights accurate.",
                detailLine = "",
                nextSuggestion = "Open today’s planned meal instead, then log it after eating.",
                nextRoute = "progress/today",
                nextCtaLabel = "Open Progress",
            ),
            detailsExpanded = true,
            onToggleDetails = {},
            onNavigateToRoute = {},
        )
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = false,
        ) {
            Text("Mark as Eaten (Today)", fontWeight = FontWeight.Bold)
        }
        Text(
            text = "You can only log meals that appear in today’s plan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Add to Grocery List")
        }
        PreviewRecipeHeroHeader(
            title = "Tofu Sisig Lettuce Cups",
            emoji = "\uD83E\uDD57",
        )
    }
}

@Preview(
    name = "Recipe Meal Check-In",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
)
@Composable
fun RecipeMealCheckInHostPreview() {
    PCOSINATheme {
        MealCheckInDialog(
            goal = "Weight Loss",
            mealTitle = "Ginataang Gulay with Tofu",
            mealLabel = "Lunch",
            initial = MealCheckIn(
                mealKey = "Lunch::recipe-ginataang-gulay",
                recipeId = "recipe-ginataang-gulay",
                mealLabel = "Lunch",
                energyLevel = 4,
                fullnessLevel = 4,
                cravingsLevel = 2,
                satisfactionLevel = 5,
                note = "Filling, easy to portion, and did not trigger mid-afternoon cravings.",
            ),
            onDismiss = {},
            onSave = {},
        )
    }
}

@Preview(
    name = "Recipe Add To Grocery",
    widthDp = 360,
    heightDp = 1180,
    showBackground = true,
)
@Composable
fun RecipeAddToGroceryHostPreview() {
    PreviewSurface {
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Added 4 ingredients from Ginataang Gulay with Tofu to Grocery.",
            ),
        )
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Mark as Eaten (Today)", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Add to Grocery List")
        }
        PreviewRecipeHeroHeader(
            title = "Ginataang Gulay with Tofu",
            emoji = "\uD83E\uDD57",
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "LUNCH",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Ginataang Gulay with Tofu",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "\u23F1 25 min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Primary-user plan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Ingredients and grocery totals stay scoped to the primary-user plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Ingredients",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PreviewIngredientRow("Tofu", "3 blocks")
                    PreviewIngredientRow("Pechay", "2 bunches")
                    PreviewIngredientRow("Squash", "600 g")
                    PreviewIngredientRow("Coconut milk", "3 cups")
                }
            }
            Text(
                text = "Ready to shop? Grocery should now reflect these ingredients in local state.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Recipe Admin Rationale",
    widthDp = 360,
    heightDp = 1180,
    showBackground = true,
)
@Composable
fun RecipeAdminRationaleHostPreview() {
    PreviewSurface {
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Admin review mode is showing the raw solver and ranking reasons for this meal.",
            ),
        )
        PreviewRecipeHeroHeader(
            title = "Tofu Sisig Lettuce Cups",
            emoji = "\uD83E\uDD57",
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "DINNER",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Tofu Sisig Lettuce Cups",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Why selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\u2022 Stage-1 shortlist boost matched pantry-first tofu and low-prep constraints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\u2022 MILP kept weekly budget feasible while protecting protein and fiber minima.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\u2022 Variety pressure reduced repeat dinners compared with the previous candidate set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RecipeImpactSummarySection(
            summary = RecipeImpactSummary(
                headline = "Nice progress. Today 2/3 meals • 980/1450 kcal.",
                detailLine = "74/105 g protein • 21/30 g fiber",
                nextSuggestion = "Admin mode should still leave the next action obvious instead of becoming a wall of diagnostics.",
                nextRoute = "recipe/next-meal",
                nextCtaLabel = "Open Next Meal",
            ),
            detailsExpanded = true,
            onToggleDetails = {},
            onNavigateToRoute = {},
        )
    }
}

@Preview(
    name = "Recipe Not In Today Plan",
    widthDp = 360,
    heightDp = 1100,
    showBackground = true,
)
@Composable
fun RecipeNotInTodayPlanHostPreview() {
    PreviewSurface {
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "No change: this recipe is not in today’s plan.",
            ),
        )
        RecipeImpactSummarySection(
            summary = RecipeImpactSummary(
                headline = "Logging is locked for this recipe today.",
                detailLine = "",
                nextSuggestion = "Only meals in today’s plan can be logged. Open your next planned meal.",
                nextRoute = "dashboard/today",
                nextCtaLabel = "Open Today Hub",
            ),
            detailsExpanded = true,
            onToggleDetails = {},
            onNavigateToRoute = {},
        )
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = false,
        ) {
            Text("Not In Today’s Plan", fontWeight = FontWeight.Bold)
        }
        Text(
            text = "You can only log meals that appear in today’s plan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Add to Grocery List")
        }
        PreviewRecipeHeroHeader(
            title = "Chicken Tinola Bowl",
            emoji = "\uD83C\uDF72",
        )
    }
}

@Preview(
    name = "Grocery Pantry Flow",
    widthDp = 360,
    heightDp = 1280,
    showBackground = true,
)
@Composable
fun GroceryPantryFlowHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review pantry-first planning cues and chip readability before phone testing resumes.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Pantry updated: added monggo.",
            ),
        )
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Search ingredients") },
            placeholder = { Text("Try: egg, spinach, fish") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Showing 8 of 12 items • Need to buy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExpandableSection(
            title = "Pantry Inventory",
            subtitle = "Optional. Use-first items for planning",
            defaultExpanded = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Items here are treated as \"use-first\" during planning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = "monggo",
                    onValueChange = {},
                    label = { Text("Pantry item") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = "500 g",
                        onValueChange = {},
                        label = { Text("Qty") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = "2026-04-24",
                        onValueChange = {},
                        label = { Text("Expiry YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Add to Pantry")
                }
                Text(
                    text = "Tap the X to remove an item.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PreviewPantryChip("monggo • 500 g • exp 2026-04-24")
                    PreviewPantryChip("tofu • 3 blocks")
                    PreviewPantryChip("pechay • 2 bunches • exp 2026-04-21")
                }
            }
        }
        PreviewNarrativeCard(
            title = "What to do next",
            body = "Filter to Need to buy, check items as you shop, then open Progress to log meals.",
        )
    }
}

@Preview(
    name = "Grocery Filters Reset",
    widthDp = 360,
    heightDp = 1100,
    showBackground = true,
)
@Composable
fun GroceryFilterResetHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review recovery after a dead-end filter or search combination.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Filters reset. Showing all items.",
            ),
        )
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Search ingredients") },
            placeholder = { Text("Try: egg, spinach, fish") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Showing 14 of 14 items • All items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TokenizedFilterChip(
                selected = true,
                text = "All items",
                onClick = {},
                labelMaxWidth = 92.dp,
            )
            TokenizedFilterChip(
                selected = false,
                text = "Need to buy",
                onClick = {},
                labelMaxWidth = 110.dp,
            )
            TokenizedFilterChip(
                selected = false,
                text = "Bought / pantry",
                onClick = {},
                labelMaxWidth = 122.dp,
            )
        }
        PreviewNarrativeCard(
            title = "Shopping overview",
            body = "Built for the primary-user plan. Totals use a local market guide and may change week to week.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "Need to buy",
                value = "9 items",
                subtitle = "Filters cleared",
                footer = "Back to full list",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Estimated total",
                value = "\u20B11680",
                subtitle = "Weekly guide",
                footer = "Within budget",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(
    name = "Grocery Budget Warning",
    widthDp = 360,
    heightDp = 1160,
    showBackground = true,
)
@Composable
fun GroceryBudgetWarningHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review the over-budget state before a user reaches checkout.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "Estimated total is over your weekly budget. Review cheaper swaps or pantry-first meals before shopping.",
            ),
        )
        PreviewNarrativeCard(
            title = "Shopping overview",
            body = "Built for the primary-user plan. Totals use a local market guide and may change week to week.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewSummaryTile(
                title = "Need to buy",
                value = "11 items",
                modifier = Modifier.weight(1f),
            )
            PreviewSummaryTile(
                title = "Estimated total",
                value = "\u20B12140",
                modifier = Modifier.weight(1f),
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("Weekly") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                    FilterChip(
                        selected = false,
                        onClick = {},
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("Monthly (\u2248 weekly \u00D7 4.33)") },
                    )
                }
                Text(
                    text = "Over budget",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Estimated \u20B12140 for the primary-user plan against a \u20B11800 weekly budget. About \u20B1340 over.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Budget is projected, not actual. Log actual spending in Progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Preview(
    name = "Grocery Budget Monthly",
    widthDp = 360,
    heightDp = 1160,
    showBackground = true,
)
@Composable
fun GroceryBudgetMonthlyHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review the monthly budget mode copy and chip emphasis.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Monthly budget mode helps explain the weekly guide without implying actual spend.",
            ),
        )
        PreviewNarrativeCard(
            title = "Shopping overview",
            body = "Built for the primary-user plan. Totals use a local market guide and may change week to week.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewSummaryTile(
                title = "Need to buy",
                value = "8 items",
                modifier = Modifier.weight(1f),
            )
            PreviewSummaryTile(
                title = "Estimated total",
                value = "\u20B11620",
                modifier = Modifier.weight(1f),
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {},
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("Weekly") },
                    )
                    FilterChip(
                        selected = true,
                        onClick = {},
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("Monthly (\u2248 weekly \u00D7 4.33)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
                Text(
                    text = "Within budget",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Estimated \u20B11620 for the primary-user plan against a \u20B17800 monthly budget. Weekly guide: \u20B1180.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Budget is projected, not actual. Log actual spending in Progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { 0.9f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PcosinaSuccess,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Preview(
    name = "Grocery Share Failure",
    widthDp = 360,
    heightDp = 1200,
    showBackground = true,
)
@Composable
fun GroceryShareFailureHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review failure recovery when Android cannot open share targets.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "Couldn’t open share options on this device.",
                actionLabel = "Try again",
            ),
            onAction = {},
        )
        PreviewNarrativeCard(
            title = "Shopping overview",
            body = "Built for the primary-user plan. Totals use a local market guide and may change week to week.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewSummaryTile(
                title = "Need to buy",
                value = "8 items",
                modifier = Modifier.weight(1f),
            )
            PreviewSummaryTile(
                title = "Estimated total",
                value = "\u20B11620",
                modifier = Modifier.weight(1f),
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Within budget",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Estimated \u20B11620 for the primary-user plan against a \u20B11800 weekly budget. About \u20B1180 left.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { 0.9f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PcosinaSuccess,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text("Share List")
                }
            }
        }
    }
}

@Preview(
    name = "Grocery Share Success",
    widthDp = 360,
    heightDp = 1200,
    showBackground = true,
)
@Composable
fun GroceryShareSuccessHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review the share-success path so it feels resolved rather than ambiguous.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Share options opened for your grocery list.",
            ),
        )
        PreviewNarrativeCard(
            title = "Shopping overview",
            body = "Built for the primary-user plan. Totals use a local market guide and may change week to week.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewSummaryTile(
                title = "Need to buy",
                value = "8 items",
                modifier = Modifier.weight(1f),
            )
            PreviewSummaryTile(
                title = "Estimated total",
                value = "\u20B11620",
                modifier = Modifier.weight(1f),
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Within budget",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Estimated \u20B11620 for the primary-user plan against a \u20B11800 weekly budget. About \u20B1180 left.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { 0.9f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PcosinaSuccess,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text("Share List")
                }
            }
        }
    }
}

@Preview(
    name = "Grocery Checked Items",
    widthDp = 360,
    heightDp = 1280,
    showBackground = true,
)
@Composable
fun GroceryCheckedItemsHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review checked, pantry, and remaining item contrast on the real shopping flow.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "You have 3 items left to buy. Pantry-first items stay marked as complete until you opt out.",
            ),
        )
        Text(
            text = "Showing 6 of 6 items • Bought / pantry",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "\uD83E\uDD6C", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Produce",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "\u2022 3 items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PreviewCheckedGroceryRow(
                    name = "Pechay",
                    quantity = "2 bunches",
                    price = "\u20B10",
                    checked = true,
                    pantryMatch = true,
                )
                PreviewCheckedGroceryRow(
                    name = "Malunggay",
                    quantity = "1 bundle",
                    price = "\u20B10",
                    checked = true,
                    pantryMatch = false,
                )
                PreviewCheckedGroceryRow(
                    name = "Tomatoes",
                    quantity = "4 pcs",
                    price = "\u20B145",
                    checked = false,
                    pantryMatch = false,
                )
            }
        }
    }
}

@Preview(
    name = "Grocery Checked Pantry Category",
    widthDp = 360,
    heightDp = 1320,
    showBackground = true,
)
@Composable
fun GroceryCheckedPantryCategoryHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Grocery List",
            subtitle = "Review checked-item readability in a second category beyond Produce.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Pantry-first proteins and canned goods are marked complete until you opt out.",
            ),
        )
        Text(
            text = "Showing 5 of 5 items • Bought / pantry",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "\uD83E\uDD6B", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Protein & pantry",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "\u2022 3 items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PreviewCheckedGroceryRow(
                    name = "Tofu",
                    quantity = "3 blocks",
                    price = "\u20B10",
                    checked = true,
                    pantryMatch = true,
                )
                PreviewCheckedGroceryRow(
                    name = "Monggo",
                    quantity = "500 g",
                    price = "\u20B10",
                    checked = true,
                    pantryMatch = true,
                )
                PreviewCheckedGroceryRow(
                    name = "Eggs",
                    quantity = "1 tray",
                    price = "\u20B1280",
                    checked = false,
                    pantryMatch = false,
                )
            }
        }
    }
}

@Preview(
    name = "Settings Overview",
    widthDp = 360,
    heightDp = 1700,
    showBackground = true,
)
@Composable
fun SettingsOverviewHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Notifications active for reminders and plan updates.",
            workersSummary = "Scheduled workers: breakfast, lunch, dinner, and weekly reset are active.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: update profile rules only if your plan or pantry no longer fits the week.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Settings refreshed from local data. Reminder schedule and profile rules are in sync.",
            ),
        )
        PreviewSettingsProfileSummaryCard(
            userName = "Salva",
            goal = "Symptom Management",
            reminderState = "Reminders on \u2022 Permission Allowed",
        )
        SettingsSection(title = "Health Markers") {
            SettingsItem(
                icon = Icons.Filled.Info,
                label = "Current Weight",
                value = "68 kg",
            )
            SettingsItem(
                icon = Icons.Filled.Info,
                label = "Height",
                value = "158 cm",
            )
            SettingsItem(
                icon = Icons.Filled.Info,
                label = "Activity Level",
                value = "Moderately Active",
            )
            SettingsItem(
                icon = Icons.Filled.Info,
                label = "BMI",
                value = "27.2 (Overweight)",
            )
            SettingsItem(
                icon = Icons.Filled.Info,
                label = "Current Focus",
                value = "Symptom Management",
            )
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Update profile & planning rules", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Includes profile, food rules, cooking limits, and budget.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsSection(
            title = "Danger Zone",
            titleColor = MaterialTheme.colorScheme.error,
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ) {
            Text(
                text = "Local device actions only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Clear local meal history",
                description = "Delete plans, groceries, and logs on this device",
                color = MaterialTheme.colorScheme.error,
                destructive = true,
            ) {}
            SettingsActionItem(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Logout",
                description = "Sign out from this device",
                color = MaterialTheme.colorScheme.error,
                destructive = true,
            ) {}
        }
        PreviewNarrativeCard(
            title = "Admin hint",
            body = "Long-press the header to enable Admin mode and reveal internal review tools.",
        )
    }
}

@Preview(
    name = "Settings Admin Tools",
    widthDp = 360,
    heightDp = 1580,
    showBackground = true,
)
@Composable
fun SettingsAdminToolsHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Admin mode enabled. Internal references and debug actions are visible.",
            workersSummary = "Scheduled workers: meal reminders, weekly reset, and plan-ready paths remain active.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: use methodology and schema tools only for internal review.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Admin mode enabled. System actions are now visible.",
            ),
        )
        PreviewSettingsProfileSummaryCard(
            userName = "Salva",
            goal = "Weight Loss",
            reminderState = "Reminders on \u2022 Permission Allowed",
        )
        SettingsSection(title = "Admin Tools") {
            SettingsItem(
                icon = Icons.Filled.Info,
                label = "Schema Version",
                value = "1.5.0",
            )
            SettingsItem(
                icon = Icons.Filled.History,
                label = "API Base URL",
                value = "https://pcosina.example/api",
            )
            SettingsActionItem(
                icon = Icons.Filled.Share,
                label = "Open API contract",
                description = "https://pcosina.example/schema/pcosina_contract.json",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.Settings,
                label = "Open planning methodology",
                description = "Open the admin-only technical pipeline view",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Seed demo weeks",
                description = "Generate 3 weeks of demo plans + progress",
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
        PreviewNarrativeCard(
            title = "Internal review only",
            body = "These tools are for schema checks, methodology review, and demo data seeding. Regular users should stay in the standard plan, grocery, progress, and support flows.",
        )
    }
}

@Preview(
    name = "Settings Notifications Permission",
    widthDp = 360,
    heightDp = 1360,
    showBackground = true,
)
@Composable
fun SettingsNotificationsPermissionHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Reminders paused until notification permission is granted.",
            workersSummary = "No active notification workers.",
            permissionState = "Permission state: Permission not granted",
            nextAction = "Next focus: open Android notification settings and allow local reminders.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "No change: notification permission was denied.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = false,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Permission not granted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionItem(
                icon = Icons.Filled.Info,
                label = "Open system notification settings",
                description = "Allow notifications for PCOSINA in Android settings",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Permission denied. Enable notifications from system settings to receive reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Meal reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Breakfast, lunch, and dinner reminders (max 3/day).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Settings Notifications Success",
    widthDp = 360,
    heightDp = 1480,
    showBackground = true,
)
@Composable
fun SettingsNotificationsSuccessHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Notifications active for reminders and plan updates.",
            workersSummary = "Scheduled workers: meal reminders, weekly reset, and plan-ready paths are active.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: confirm reminder times and weekly reset scheduling.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Notifications enabled. Meal and weekly reminders can now run.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Meal reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Breakfast, lunch, and dinner reminders (max 3/day).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Breakfast time",
                description = "7:00 AM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Lunch time",
                description = "12:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Dinner time",
                description = "6:30 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Plan ready notification", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Sent when a weekly plan generation finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Grocery sync updates", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Shown only when you manually start a grocery sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weekly reset reminder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Weekly reminder to generate your next plan (max 1/week).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Weekly reset day",
                description = "Sunday",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Weekly reset time",
                description = "7:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Settings Quiet Hours Schedule",
    widthDp = 360,
    heightDp = 1580,
    showBackground = true,
)
@Composable
fun SettingsQuietHoursScheduleHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Notifications active with quiet hours enabled.",
            workersSummary = "Scheduled workers defer overnight reminders to the next allowed window.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: verify quiet-hours timing and deferred reminder copy.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Quiet hours active from 10:00 PM to 6:00 AM. Routine reminders pause overnight.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Meal reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Breakfast, lunch, and dinner reminders (max 3/day).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Breakfast time",
                description = "7:00 AM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Lunch time",
                description = "12:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Dinner time",
                description = "6:30 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weekly reset reminder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Weekly reminder to generate your next plan (max 1/week).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Weekly reset day",
                description = "Sunday",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Weekly reset time",
                description = "7:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quiet hours", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Pauses routine reminders within selected time window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Quiet hours start",
                description = "10:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Quiet hours end",
                description = "6:00 AM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Routine reminders that fall inside quiet hours are deferred until the next allowed window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Settings Meal Reminder Success",
    widthDp = 360,
    heightDp = 1720,
    showBackground = true,
)
@Composable
fun SettingsMealReminderSuccessHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Meal reminders active for breakfast, lunch, and dinner.",
            workersSummary = "Scheduled workers refreshed after immediate dispatch.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: verify last-delivered and worker-log copy.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Meal reminder delivered. Scheduling state was refreshed.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Breakfast time",
                description = "7:00 AM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Lunch time",
                description = "12:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Dinner time",
                description = "6:30 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Send meal reminder now",
                description = "Fires meal reminder path with caps and quiet-hours checks",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Scheduled workers",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "\u2022 Meal reminders active for breakfast, lunch, and dinner windows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2022 Weekly reset worker queued for Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last delivered notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "\u2022 Lunch reminder sent Apr 19 at 12:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2022 Scheduling logs refreshed after immediate dispatch",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Settings Weekly Reset Success",
    widthDp = 360,
    heightDp = 1720,
    showBackground = true,
)
@Composable
fun SettingsWeeklyResetSuccessHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Weekly reset reminder is active.",
            workersSummary = "Scheduled workers refreshed after weekly dispatch.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: verify weekly reset timing and delivery copy.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Weekly reset reminder delivered. Scheduling state was refreshed.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weekly reset reminder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Weekly reminder to generate your next plan (max 1/week).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Weekly reset day",
                description = "Sunday",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Weekly reset time",
                description = "7:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Send weekly reset now",
                description = "Fires weekly reset path with 7-day cap checks",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Scheduled workers",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "\u2022 Meal reminders active for breakfast, lunch, and dinner windows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2022 Weekly reset worker queued for Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last delivered notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "\u2022 Weekly reset reminder sent Apr 19 at 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2022 Delivery logs refreshed after dispatch",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Settings Test Notification Success",
    widthDp = 360,
    heightDp = 1660,
    showBackground = true,
)
@Composable
fun SettingsTestNotificationSuccessHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Debug test notification path is available.",
            workersSummary = "Scheduled workers remain active after test dispatch.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: confirm debug actions read as internal tools.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "Test notification sent. Delivery logs were updated.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionItem(
                icon = Icons.Filled.Info,
                label = "Send test notification",
                description = "Triggers a local debug notification",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Send meal reminder now",
                description = "Fires meal reminder path with caps and quiet-hours checks",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Send weekly reset now",
                description = "Fires weekly reset path with 7-day cap checks",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Scheduled workers",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "\u2022 Meal reminders active for breakfast, lunch, and dinner windows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2022 Weekly reset worker queued for Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last delivered notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "\u2022 Debug test notification sent Apr 19 at 10:45 AM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2022 Generic lock-screen copy preserved for privacy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Settings Reminder No Sign-In",
    widthDp = 360,
    heightDp = 1580,
    showBackground = true,
)
@Composable
fun SettingsReminderNoSignInHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Reminders are configured, but debug sends require sign-in.",
            workersSummary = "No active notification workers.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: make the sign-in requirement obvious before dispatch.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "No change: sign in first to send meal reminders.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Breakfast time",
                description = "7:00 AM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Lunch time",
                description = "12:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Dinner time",
                description = "6:30 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Send meal reminder now",
                description = "Fires meal reminder path with caps and quiet-hours checks",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Sign in before debug reminder actions can dispatch notifications or refresh delivery logs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Scheduled workers",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "No active notification workers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last delivered notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "No notifications delivered yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Settings Reminder Diagnostics",
    widthDp = 360,
    heightDp = 1600,
    showBackground = true,
)
@Composable
fun SettingsReminderDiagnosticsHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Hi, Salva! \u2728",
            subtitle = "Reminders, privacy, and profile controls stay local-first.",
            containerHeight = 180,
        )
        PreviewSettingsStatusHeader(
            remindersSummary = "Reminder dispatch is blocked by caps, quiet hours, permission, or session state.",
            workersSummary = "No active notification workers.",
            permissionState = "Permission state: Allowed",
            nextAction = "Next focus: check whether the failure reason reads clearly enough to recover.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Error,
                message = "No reminder sent: blocked by caps, quiet hours, permission, or session.",
            ),
        )
        SettingsSection(title = "Notifications & Reminders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow local reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                )
            }
            Text(
                text = "Permission state: Allowed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Breakfast time",
                description = "7:00 AM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Lunch time",
                description = "12:00 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Dinner time",
                description = "6:30 PM",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Send meal reminder now",
                description = "Fires meal reminder path with caps and quiet-hours checks",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            SettingsActionItem(
                icon = Icons.Filled.History,
                label = "Send weekly reset now",
                description = "Fires weekly reset path with 7-day cap checks",
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = "Next reminders: B 7:00 AM • L 12:00 PM • D 6:30 PM • Weekly Sunday 7:00 PM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Scheduled workers",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "No active notification workers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last delivered notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "No notifications delivered yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Progress Compact",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Preview(
    name = "Progress Dark",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ProgressHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Progress this week",
            subtitle = "Quick wins, logging guardrails, and the next action stay visible offline.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Loading,
                message = "2 meals logged locally. Progress sync will resume when connectivity returns.",
            ),
        )
        ProgressJumpToTodayAction(
            todayIndexInWeek = 3,
            onJump = {},
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "Adherence",
                value = "5 / 7 days",
                subtitle = "Best streak this month",
                footer = "2 more logs to beat last week",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Fiber average",
                value = "26 g",
                subtitle = "Near target for symptom support",
                footer = "Target 30 g",
                modifier = Modifier.weight(1f),
            )
        }
        ExpandableSection(
            title = "Nutrition progress",
            subtitle = "Keep the weekly trend obvious",
            defaultExpanded = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroProgressBar(
                    label = "Fiber",
                    progress = 0.86f,
                    valueText = "26 / 30 g",
                )
                MacroProgressBar(
                    label = "Protein",
                    progress = 0.78f,
                    valueText = "82 / 105 g",
                )
                MacroProgressBar(
                    label = "Added sugar",
                    progress = 0.32f,
                    valueText = "16 / 50 g",
                    barColor = MaterialTheme.colorScheme.tertiary,
                )
                ProgressLoggingPolicyLearnMoreChip(onClick = {})
            }
        }
    }
}

@Preview(
    name = "Progress Empty",
    widthDp = 360,
    heightDp = 960,
    showBackground = true,
)
@Composable
fun ProgressEmptyHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Progress this week",
            subtitle = "Review the zero-data state before real users see it after onboarding or a reset.",
        )
        PreviewNarrativeCard(
            title = "No meals logged yet",
            body = "Your weekly summary will appear here after you log breakfast, lunch, dinner, or snacks. The first empty state should explain the reward, not just announce the absence of data.",
        )
        AppFeedbackBanner(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Success,
                message = "You are ready to start logging. Local entries will save immediately even if sync is still unavailable.",
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                title = "Adherence",
                value = "0 / 7 days",
                subtitle = "No check-ins recorded yet",
                footer = "Start with today's first meal",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Fiber average",
                value = "--",
                subtitle = "Waiting for meal logs",
                footer = "Target 30 g",
                modifier = Modifier.weight(1f),
            )
        }
        ProgressJumpToTodayAction(
            todayIndexInWeek = -1,
            onJump = {},
        )
        ProgressLoggingPolicyLearnMoreChip(onClick = {})
    }
}

@Preview(
    name = "Support Guides",
    widthDp = 360,
    heightDp = 1320,
    showBackground = true,
)
@Composable
fun SupportGuidesHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Support & Guides",
            subtitle = "Practical help, meal tips, and simple support for your week.",
        )
        StatusCenterCard(
            queuedActionsLabel = "Support guides are ready for this week.",
            syncLabel = "No device needed: review guidance and recovery copy from the workstation.",
            planRangeLabel = "Recommended first: start-here guidance, quick guides, and peer support copy.",
            nextReminderLabel = "Next focus: confirm the wording feels helpful before phone testing returns.",
        )
        PreviewSupportTopicCard(
            icon = Icons.Filled.Info,
            title = "Start here this week",
            body = "Keep the first action obvious when a user feels unsure where to begin.",
            bullets = listOf(
                "Review today's meals first before changing the whole week.",
                "Use the grocery list as shopping guidance, not a strict rulebook.",
                "Log one meal today to build momentum."
            ),
        )
        PreviewSupportTopicCard(
            icon = Icons.Filled.Search,
            title = "Quick guides",
            body = "Short, practical help for common first-week questions.",
            bullets = listOf(
                "Build a realistic pantry before generating a plan.",
                "Use swaps when a suggested meal does not fit the day.",
                "Connect meal plan, grocery, and progress without leaving the guided flow."
            ),
        )
        PreviewSupportTopicCard(
            icon = Icons.Filled.History,
            title = "Peer support corner",
            body = "Reserved for future stories, shared wins, and practical learning sessions.",
            bullets = listOf(
                "Member stories and practical wins",
                "Simple weekly habit challenges",
                "Coach or peer Q&A highlights"
            ),
            badgeText = "Coming soon",
        )
        PreviewNarrativeCard(
            title = "Need support?",
            body = "Send feedback if something feels confusing, missing, or harder than it should be.",
        )
    }
}

@Preview(
    name = "Help & Tools",
    widthDp = 360,
    heightDp = 1280,
    showBackground = true,
)
@Composable
fun HelpToolsHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Help & Tools",
            subtitle = "Open support guides, send feedback, and review internal references when needed.",
        )
        StatusCenterCard(
            queuedActionsLabel = "Support guides and weekly help are ready.",
            syncLabel = "Feedback opens your email app with a prefilled message.",
            planRangeLabel = "Admin references are available in this workspace.",
            nextReminderLabel = "Next focus: guides first, then feedback, then methodology for internal review.",
        )
        PreviewHelpActionCard(
            icon = Icons.Filled.Search,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Open support guides",
            body = "Review practical help, first-week tips, and support copy without leaving the guided flow.",
            actionLabel = "Open guides",
        )
        PreviewHelpActionCard(
            icon = Icons.Filled.Email,
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Send feedback",
            body = "Report confusing screens, missing guidance, or bugs with a prefilled email draft.",
            actionLabel = "Send feedback",
        )
        PreviewHelpActionCard(
            icon = Icons.Filled.Settings,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Open planning methodology",
            body = "Review the deterministic filtering and optimization pipeline used by the planner.",
            actionLabel = "Open methodology",
        )
    }
}

@Preview(
    name = "Planning Methodology",
    widthDp = 360,
    heightDp = 1480,
    showBackground = true,
)
@Composable
fun PlanningMethodologyHostPreview() {
    PreviewSurface {
        GradientHeader(
            title = "Planning Methodology",
            subtitle = "Internal view of the deterministic planning pipeline.",
        )
        StatusCenterCard(
            queuedActionsLabel = "Deterministic filtering and optimization remain the planning backbone.",
            syncLabel = "Hard rules always win. ML can assist ranking, but never override constraints.",
            planRangeLabel = "Profile and pantry inputs stay local-first before planning starts.",
            nextReminderLabel = "Use this screen for internal review only. Regular users should stay in plan, grocery, and progress.",
        )
        PreviewMethodologyStepCard(
            step = "01",
            title = "Profile + Pantry Inputs",
            description = "Planning starts from saved profile data, pantry context, goals, budget, allergies, and exclusions.",
            bullets = listOf(
                "Profile and symptom context shape the week.",
                "Hard rules include allergies, exclusions, budget caps, and max cooking time.",
                "Local pantry context stays device-first."
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        PreviewMethodologyStepCard(
            step = "02",
            title = "Deterministic Filtering",
            description = "Recipes are screened before optimization so infeasible options never reach the final planner.",
            bullets = listOf(
                "Hard rules remove unsafe or infeasible meals first.",
                "Allergy families, pantry feasibility, and cook-time limits are enforced here.",
                "This stage remains explainable and repeatable offline."
            ),
            color = MaterialTheme.colorScheme.secondary,
        )
        PreviewMethodologyStepCard(
            step = "03",
            title = "Deterministic Optimization",
            description = "A deterministic solver chooses the final week from the feasible meal candidates.",
            bullets = listOf(
                "Balances calories, macros, variety, symptoms, and planning priorities.",
                "ML can assist ranking candidates but never override hard constraints.",
                "Shopping totals stay scoped to the primary-user plan."
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        PreviewMethodologyStepCard(
            step = "04",
            title = "Explainable Outputs",
            description = "The result is a weekly plan, grocery guidance, and fallback messaging with visible rationale.",
            bullets = listOf(
                "Weekly meals, exclusion summaries, and pantry-aware shopping guidance",
                "Recipe details and nutrition totals stay visible for review",
                "No-safe-plan cases return actionable adjustments instead of silent failure"
            ),
            color = MaterialTheme.colorScheme.secondary,
        )
        PreviewNarrativeCard(
            title = "Decision-support boundary",
            body = "PCOSina supports meal planning and nutrition decisions. It does not diagnose conditions or replace clinical care.",
        )
    }
}

@Composable
private fun PreviewNarrativeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewSettingsStatusHeader(
    remindersSummary: String,
    workersSummary: String,
    permissionState: String,
    nextAction: String,
) {
    StatusCenterCard(
        queuedActionsLabel = remindersSummary,
        syncLabel = workersSummary,
        planRangeLabel = permissionState,
        nextReminderLabel = nextAction,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreviewSettingsProfileSummaryCard(
    userName: String,
    goal: String,
    reminderState: String,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = goal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = reminderState,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewSupportTopicCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    bullets: List<String>,
    badgeText: String? = null,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    badgeText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            bullets.forEach { bullet ->
                Text(
                    text = "\u2022 $bullet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewHelpActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    actionLabel: String,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = iconTint),
            ) {
                Text(text = actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PreviewMethodologyStepCard(
    step: String,
    title: String,
    description: String,
    bullets: List<String>,
    color: androidx.compose.ui.graphics.Color,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = color.copy(alpha = 0.4f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = color,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            bullets.forEach { bullet ->
                Text(
                    text = "\u2022 $bullet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewSummaryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewCheckedGroceryRow(
    name: String,
    quantity: String,
    price: String,
    checked: Boolean,
    pantryMatch: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = {},
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (checked) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                )
                if (pantryMatch) {
                    Text(
                        text = "Use first • Pantry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = quantity,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = price,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun PreviewRecipeHeroHeader(
    title: String,
    emoji: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            )
            .padding(16.dp),
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = emoji, fontSize = 48.sp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PreviewRecipeMetric(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewIngredientRow(
    name: String,
    amount: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PreviewInstructionRow(
    step: Int,
    text: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PreviewPantryChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = true,
        onClick = {},
        modifier = modifier.heightIn(min = 48.dp),
        label = { Text(text = label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove pantry item",
            )
        },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun PreviewSkeletonTile() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(
            modifier = Modifier.size(28.dp),
            shape = MaterialTheme.shapes.small,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {}
        PreviewSkeletonBar(
            modifier = Modifier
                .widthIn(min = 36.dp)
                .height(8.dp),
        )
    }
}

@Composable
private fun PreviewSkeletonBar(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {}
}

@Composable
private fun PreviewSurface(
    content: @Composable () -> Unit,
) {
    PCOSINATheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(PaddingValues(16.dp)),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}
