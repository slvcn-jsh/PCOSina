package com.pcosina.app.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.theme.CanvaTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.util.sampleFrameTiming
import java.util.Locale

@Composable
fun CanvaLoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onDebugFirstWinContinue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val loginState by authViewModel.loginState.collectAsState()
    val loginMessage by authViewModel.loginMessage.collectAsState()
    val analytics = FirebaseAnalytics.getInstance(LocalContext.current)
    val context = LocalContext.current
    val isLoading = loginState is LoginState.Loading
    val loginErrorMessage = (loginState as? LoginState.Error)?.message
    val emailFieldError = loginErrorMessage?.contains("email", ignoreCase = true) == true
    val passwordFieldError = loginErrorMessage?.contains("password", ignoreCase = true) == true
    val showVerificationResend = loginErrorMessage?.contains("verify", ignoreCase = true) == true
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
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        CanvaLoginHeader()

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CanvaTokens.PanelPinkLight,
            shape = RoundedCornerShape(topStart = 160.dp, topEnd = 160.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.pcosina_logo),
                    contentDescription = "PCOSina logo",
                    modifier = Modifier.size(126.dp),
                    contentScale = ContentScale.Fit,
                )

                Text(
                    text = "Wellness decision support tool",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = CanvaTokens.Ink,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Welcome Back!",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                )

                bannerData?.let { banner ->
                    AppFeedbackBanner(
                        data = banner,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                CanvaLoginField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (loginState is LoginState.Error || loginMessage != null) {
                            authViewModel.clearError()
                        }
                    },
                    label = "Email Address",
                    enabled = !isLoading,
                    isError = emailFieldError,
                    keyboardType = KeyboardType.Email,
                )

                CanvaLoginField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (loginState is LoginState.Error || loginMessage != null) {
                            authViewModel.clearError()
                        }
                    },
                    label = "Password",
                    enabled = !isLoading,
                    isError = passwordFieldError,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = CanvaTokens.SupportGray,
                            )
                        }
                    },
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextButton(
                        onClick = {
                            analytics.logEvent("forgot_password_tap", null)
                            authViewModel.sendPasswordReset(email)
                        },
                        enabled = !isLoading,
                    ) {
                        Text(
                            text = "Forgot password?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                        )
                    }

                    if (showVerificationResend) {
                        TextButton(
                            onClick = { authViewModel.resendVerification(email, password) },
                            enabled = !isLoading,
                        ) {
                            Text(
                                text = "Resend verification email",
                                color = Color.White,
                            )
                        }
                    }
                }

                CanvaActionButton(
                    text = if (isLoading) "Signing in..." else "Sign in",
                    onClick = {
                        analytics.logEvent("login_attempt", null)
                        authViewModel.onLogin(email, password)
                    },
                    enabled = !isLoading,
                    filled = true,
                )

                CanvaActionButton(
                    text = "Continue with Google",
                    onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                    enabled = !isLoading,
                    filled = false,
                )

                if (emailFieldError || passwordFieldError) {
                    Text(
                        text = loginErrorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8E213B),
                        textAlign = TextAlign.Center,
                    )
                }

                TextButton(
                    onClick = onNavigateToSignUp,
                    enabled = !isLoading,
                ) {
                    Text(
                        text = "Don’t have an account? Create one",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF8F4050),
                        textAlign = TextAlign.Center,
                    )
                }

                if (BuildConfig.DEBUG && onDebugFirstWinContinue != null) {
                    TextButton(
                        onClick = onDebugFirstWinContinue,
                        enabled = !isLoading,
                    ) {
                        Text(
                            text = "Debug: Continue First-Win Flow",
                            color = Color(0xFF8F4050),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvaLoginHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(228.dp)
            .background(Color.White),
    ) {
        repeat(7) { index ->
            Surface(
                modifier = Modifier
                    .size((56 + (index % 3) * 18).dp)
                    .padding(
                        start = (18 + (index * 42)).dp,
                        top = (18 + (index % 4) * 24).dp,
                    ),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(3.dp, CanvaTokens.PanelPink.copy(alpha = 0.45f)),
            ) {}
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(82.dp),
            shape = RoundedCornerShape(topStart = 200.dp, topEnd = 200.dp),
            color = CanvaTokens.AccentPinkStrong,
        ) {}
    }
}

@Composable
private fun CanvaLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
            )
        },
        enabled = enabled,
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(22.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFFDF0F1),
            unfocusedContainerColor = Color(0xFFFDF0F1),
            disabledContainerColor = Color(0xFFF4DCDC),
            errorContainerColor = Color(0xFFFFEEF0),
            focusedBorderColor = Color(0xFFB7A8A8),
            unfocusedBorderColor = Color(0xFFB7A8A8),
            errorBorderColor = Color(0xFFB44A5A),
            focusedTextColor = CanvaTokens.Ink,
            unfocusedTextColor = CanvaTokens.Ink,
            focusedLabelColor = CanvaTokens.SupportGray,
            unfocusedLabelColor = CanvaTokens.SupportGray,
        ),
    )
}

@Composable
private fun CanvaActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (filled) CanvaTokens.AccentPinkStrong else Color(0xFFFDF0F1),
        shadowElevation = 14.dp,
        border = if (filled) null else BorderStroke(2.dp, Color(0xFFB7A8A8)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (filled) Color.White else CanvaTokens.AccentPinkStrong,
                textAlign = TextAlign.Center,
            )
        }
    }
}
