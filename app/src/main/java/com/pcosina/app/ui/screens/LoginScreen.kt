package com.pcosina.app.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.analytics.FirebaseAnalytics
import com.pcosina.app.BuildConfig
import com.pcosina.app.R
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.LoginState
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.util.sampleFrameTiming
import java.util.Locale

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onDebugFirstWinContinue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val loginState by authViewModel.loginState.collectAsState()
    val loginMessage by authViewModel.loginMessage.collectAsState()
    val analytics = FirebaseAnalytics.getInstance(LocalContext.current)
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isLoading = loginState is LoginState.Loading
    val loginErrorMessage = (loginState as? LoginState.Error)?.message
    val emailFieldError = loginErrorMessage?.contains("email", ignoreCase = true) == true
    val passwordFieldError = loginErrorMessage?.contains("password", ignoreCase = true) == true
    val showVerificationResend = loginErrorMessage?.contains("verify", ignoreCase = true) == true
    val actionState = when (loginState) {
        LoginState.Idle -> FeedbackActionState.Idle
        LoginState.Loading -> FeedbackActionState.Loading
        LoginState.Success -> FeedbackActionState.Success
        is LoginState.Error -> FeedbackActionState.Error
    }
    val bannerData = when {
        !loginMessage.isNullOrBlank() -> FeedbackBannerData(
            tone = FeedbackBannerTone.Success,
            message = loginMessage.orEmpty(),
        )
        !loginErrorMessage.isNullOrBlank() -> FeedbackBannerData(
            tone = FeedbackBannerTone.Error,
            message = loginErrorMessage.orEmpty(),
        )
        else -> null
    }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            authViewModel.onGoogleLogin(account.idToken)
        } catch (e: ApiException) {
            val message = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Google sign-in cancelled."
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Network issue during Google sign-in. Please try again."
                GoogleSignInStatusCodes.DEVELOPER_ERROR ->
                    "Google sign-in is not available on this build yet. Use email sign-in for now."
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Google sign-in failed. Please try again."
                else -> "Google sign-in failed (code ${e.statusCode})."
            }
            authViewModel.onGoogleLoginFailure(message)
        } catch (e: Exception) {
            authViewModel.onGoogleLoginFailure("Google sign-in failed. ${e.message ?: ""}".trim())
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            analytics.logEvent("login_success", null)
            onLoginSuccess()
        }
    }
    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            val stats = sampleFrameTiming(
                windowMs = UiMotionTokens.MotionFrameProbeWindowMs,
                jankThresholdMs = UiMotionTokens.FrameJankThresholdMs,
            )
            Log.i(
                "LoginMotion",
                "frames=${stats.frames} avg=${"%.1f".format(Locale.ENGLISH, stats.avgFrameMs)}ms " +
                    "p95=${"%.1f".format(Locale.ENGLISH, stats.p95FrameMs)}ms " +
                    "max=${"%.1f".format(Locale.ENGLISH, stats.worstFrameMs)}ms " +
                    "jank=${stats.jankFrames}/${stats.frames}",
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PCOSINA",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            color = colorScheme.secondary,
        )
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground,
        )
        Text(
            text = "Sign in to open your saved plan, grocery list, and progress from this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_first_win_card"),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Fastest path back in",
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
            text = "Wellness decision support, not diagnosis.",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Step 1 of 6: Sign in",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_step1_label"),
        )

        bannerData?.let { banner ->
            AppFeedbackBanner(
                data = banner,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (loginState is LoginState.Error || loginMessage != null) {
                    authViewModel.clearError()
                }
            },
            label = { Text("Email Address") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_email_input"),
            enabled = !isLoading,
            singleLine = true,
            isError = emailFieldError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = {
                Text(
                    text = if (emailFieldError) {
                        loginErrorMessage.orEmpty()
                    } else {
                        "Use the email tied to your saved plan and progress."
                    },
                )
            },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                focusedLabelColor = colorScheme.primary,
            ),
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (loginState is LoginState.Error || loginMessage != null) {
                    authViewModel.clearError()
                }
            },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    enabled = !isLoading,
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_password_input"),
            enabled = !isLoading,
            singleLine = true,
            isError = passwordFieldError,
            supportingText = {
                if (passwordFieldError) {
                    Text(loginErrorMessage.orEmpty())
                }
            },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                focusedLabelColor = colorScheme.primary,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    analytics.logEvent("forgot_password_tap", null)
                    authViewModel.sendPasswordReset(email)
                },
                enabled = !isLoading,
            ) {
                Text("Forgot password?")
            }
        }

        if (showVerificationResend) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { authViewModel.resendVerification(email, password) },
                    enabled = !isLoading,
                ) {
                    Text("Resend verification email", color = colorScheme.primary)
                }
            }
        }

        LoadingActionButton(
            state = actionState,
            idleLabel = "Sign in",
            loadingLabel = "Signing in",
            successLabel = "Signed in",
            errorLabel = "Fix and retry",
            onClick = {
                analytics.logEvent("login_attempt", null)
                authViewModel.onLogin(email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("login_primary_cta"),
        )

        OutlinedButton(
            onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = !isLoading,
        ) {
            Text(
                text = "Continue with Google",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        TextButton(
            onClick = onNavigateToSignUp,
            enabled = !isLoading,
        ) {
            Text("Don't have an account? Create one", color = colorScheme.secondary)
        }

        if (BuildConfig.DEBUG && onDebugFirstWinContinue != null) {
            TextButton(
                onClick = onDebugFirstWinContinue,
                modifier = Modifier.testTag("login_debug_continue_first_win"),
                enabled = !isLoading,
            ) {
                Text("Debug: Continue First-Win Flow", color = colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
