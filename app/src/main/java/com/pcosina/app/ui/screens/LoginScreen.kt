package com.pcosina.app.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.theme.PcosinaBlush
import com.pcosina.app.ui.theme.PcosinaBlushBorder
import com.pcosina.app.ui.theme.PcosinaBlushStrong
import com.pcosina.app.ui.theme.PcosinaBlushSurface
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMidnight
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaRoseShadow
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.util.sampleFrameTiming
import java.util.Locale

@Composable
fun LoginScreen(
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        val scrollState = rememberScrollState()
        val compact = maxHeight < 900.dp || maxWidth < 400.dp
        val contentModifier = Modifier
            .verticalScroll(scrollState)
            .imePadding()
        val sheetTop = maxHeight * if (compact) 0.27f else 0.33f
        val sheetFlatTop = sheetTop + if (compact) 72.dp else 88.dp
        val outerArcSize = maxWidth * if (compact) 1.48f else 1.66f
        val innerArcSize = maxWidth * if (compact) 1.34f else 1.5f
        val outerArcTop = sheetTop - outerArcSize / 4.6f
        val innerArcTop = sheetTop - innerArcSize / 4.35f
        val contentTopPadding = sheetTop + if (compact) 6.dp else 20.dp
        val welcomeShadow = Shadow(
            color = Color.Black.copy(alpha = 0.12f),
            offset = Offset(0f, 7f),
            blurRadius = 12f,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LoginHeroPattern(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetFlatTop + 20.dp)
                    .statusBarsPadding(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = sheetFlatTop)
                    .background(PcosinaBlush),
            )

            Box(
                modifier = Modifier
                    .size(outerArcSize)
                    .align(Alignment.TopCenter)
                    .offset(y = outerArcTop)
                    .background(PcosinaBlushStrong, CircleShape),
            )

            Box(
                modifier = Modifier
                    .size(innerArcSize)
                    .align(Alignment.TopCenter)
                    .offset(y = innerArcTop)
                    .background(PcosinaBlush, CircleShape),
            )

            Column(
                modifier = contentModifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = if (compact) 22.dp else 28.dp)
                    .padding(top = contentTopPadding, bottom = if (compact) 28.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.pcosina_logo),
                    contentDescription = "PCOSina logo",
                    modifier = Modifier.size(if (compact) 112.dp else 138.dp),
                )

                Text(
                    text = "Wellness decision support tool",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = if (compact) 16.sp else 19.sp,
                    ),
                    color = PcosinaMidnight,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Welcome Back!",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 36.sp else 44.sp,
                        lineHeight = if (compact) 38.sp else 46.sp,
                        shadow = welcomeShadow,
                    ),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                )

                bannerData?.let { banner ->
                    AppFeedbackBanner(
                        data = banner,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                    )
                }

                LoginInputField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (loginState is LoginState.Error || loginMessage != null) {
                            authViewModel.clearError()
                        }
                    },
                    placeholder = "Email Address",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .testTag("login_email_input"),
                    enabled = !isLoading,
                    isError = emailFieldError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )

                LoginInputField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (loginState is LoginState.Error || loginMessage != null) {
                            authViewModel.clearError()
                        }
                    },
                    placeholder = "Password",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .testTag("login_password_input"),
                    enabled = !isLoading,
                    isError = passwordFieldError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = PcosinaMuted,
                            )
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            analytics.logEvent("forgot_password_tap", null)
                            authViewModel.sendPasswordReset(email)
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text(
                            text = "Forgot password?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            ),
                        )
                    }
                }

                if (showVerificationResend) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { authViewModel.resendVerification(email, password) },
                            enabled = !isLoading,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Text(
                                text = "Resend verification email",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        analytics.logEvent("login_attempt", null)
                        authViewModel.onLogin(email, password)
                    },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PcosinaBlushStrong,
                        disabledContainerColor = PcosinaBlushStrong.copy(alpha = 0.72f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.9f),
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .height(if (compact) 72.dp else 84.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = PcosinaRoseShadow.copy(alpha = 0.32f),
                            ambientColor = PcosinaRoseShadow.copy(alpha = 0.22f),
                        )
                        .testTag("login_primary_cta"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.6.dp,
                            )
                        }
                        Text(
                            text = when (loginState) {
                                LoginState.Loading -> "Signing in..."
                                LoginState.Success -> "Signed in"
                                else -> "Sign in"
                            },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 21.sp else 24.sp,
                            ),
                        )
                    }
                }

                OutlinedButton(
                    onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, PcosinaBlushBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PcosinaBlushSurface,
                        contentColor = PcosinaBlushStrong,
                        disabledContainerColor = PcosinaBlushSurface.copy(alpha = 0.72f),
                        disabledContentColor = PcosinaBlushStrong.copy(alpha = 0.72f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .height(if (compact) 68.dp else 80.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(22.dp),
                            spotColor = PcosinaRoseShadow.copy(alpha = 0.16f),
                            ambientColor = PcosinaRoseShadow.copy(alpha = 0.12f),
                        ),
                ) {
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 18.sp else 21.sp,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 4.dp else 18.dp))

                TextButton(
                    onClick = onNavigateToSignUp,
                    enabled = !isLoading,
                ) {
                    Text(
                        text = "Don't have an account? Create one",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PcosinaDeepRose.copy(alpha = 0.8f),
                        ),
                    )
                }

                if (BuildConfig.DEBUG && onDebugFirstWinContinue != null) {
                    TextButton(
                        onClick = onDebugFirstWinContinue,
                        modifier = Modifier.testTag("login_debug_continue_first_win"),
                        enabled = !isLoading,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text("Debug: Continue First-Win Flow")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        PcosinaBlushBorder
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 20.sp,
                    color = PcosinaMuted,
                ),
            )
        },
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            color = PcosinaMidnight,
        ),
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = PcosinaBlushSurface,
            focusedContainerColor = PcosinaBlushSurface,
            disabledContainerColor = PcosinaBlushSurface.copy(alpha = 0.72f),
            unfocusedBorderColor = borderColor,
            focusedBorderColor = borderColor,
            errorBorderColor = MaterialTheme.colorScheme.error,
            unfocusedPlaceholderColor = PcosinaMuted,
            focusedPlaceholderColor = PcosinaMuted,
            cursorColor = PcosinaDeepRose,
        ),
        modifier = modifier
            .height(78.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                spotColor = PcosinaRoseShadow.copy(alpha = 0.12f),
                ambientColor = PcosinaRoseShadow.copy(alpha = 0.08f),
            )
            .border(width = 1.dp, color = Color.Transparent, shape = shape),
    )
}

@Composable
private fun LoginHeroPattern(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val accent = PcosinaBlushStrong.copy(alpha = 0.9f)
        val lightStroke = Stroke(width = 6f, cap = StrokeCap.Round)

        fun dot(x: Float, y: Float, radius: Float) {
            drawCircle(color = accent, radius = radius, center = Offset(size.width * x, size.height * y))
        }

        fun ring(x: Float, y: Float, radius: Float) {
            drawCircle(
                color = accent,
                radius = radius,
                center = Offset(size.width * x, size.height * y),
                style = Stroke(width = 6f),
            )
        }

        fun sparkle(x: Float, y: Float, arm: Float) {
            val cx = size.width * x
            val cy = size.height * y
            drawLine(accent, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 6f, cap = StrokeCap.Round)
        }

        drawArc(
            color = accent,
            startAngle = 180f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(size.width * 0.04f, size.height * 0.2f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.2f),
            style = lightStroke,
        )
        drawArc(
            color = accent,
            startAngle = 5f,
            sweepAngle = 200f,
            useCenter = false,
            topLeft = Offset(size.width * 0.78f, size.height * 0.22f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.2f, size.height * 0.18f),
            style = lightStroke,
        )
        drawArc(
            color = accent,
            startAngle = 200f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(size.width * 0.46f, size.height * 0.03f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height * 0.11f),
            style = lightStroke,
        )

        ring(0.12f, 0.48f, 24f)
        ring(0.85f, 0.62f, 16f)
        ring(0.55f, 0.18f, 18f)
        dot(0.22f, 0.3f, 5f)
        dot(0.28f, 0.64f, 6f)
        dot(0.62f, 0.34f, 5f)
        dot(0.73f, 0.54f, 4f)
        dot(0.92f, 0.4f, 5f)

        sparkle(0.18f, 0.12f, 18f)
        sparkle(0.74f, 0.14f, 16f)
        sparkle(0.36f, 0.56f, 14f)
        sparkle(0.9f, 0.2f, 12f)

        drawLine(
            color = accent,
            start = Offset(size.width * 0.36f, size.height * 0.18f),
            end = Offset(size.width * 0.44f, size.height * 0.38f),
            strokeWidth = 7f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = Offset(size.width * 0.42f, size.height * 0.17f),
            end = Offset(size.width * 0.51f, size.height * 0.32f),
            strokeWidth = 7f,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = accent,
            startAngle = 235f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(size.width * 0.12f, size.height * 0.54f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height * 0.16f),
            style = Stroke(width = 7f),
        )
        drawArc(
            color = accent,
            startAngle = 220f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(size.width * 0.66f, size.height * 0.38f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.21f, size.height * 0.15f),
            style = Stroke(width = 7f),
        )
        drawArc(
            color = accent,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.82f, size.height * 0.62f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.1f, size.height * 0.06f),
            style = Stroke(width = 7f),
        )
    }
}
