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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    onLoginSuccess: (Boolean) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onDebugFirstWinContinue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var operatorMode by rememberSaveable { mutableStateOf(false) }
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
            onLoginSuccess(operatorMode)
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
        val compact = maxHeight < 790.dp || maxWidth < 400.dp
        val allowScroll = maxHeight < 660.dp || maxWidth < 350.dp
        val contentModifier = if (allowScroll) {
            Modifier
                .verticalScroll(scrollState)
                .imePadding()
        } else {
            Modifier.imePadding()
        }
        val sheetTop = maxHeight * if (compact) 0.27f else 0.3f
        val sheetFlatTop = sheetTop + if (compact) 62.dp else 76.dp
        val outerArcSize = maxWidth * if (compact) 1.46f else 1.62f
        val innerArcSize = maxWidth * if (compact) 1.3f else 1.44f
        val outerArcTop = sheetTop - outerArcSize / 5f
        val innerArcTop = sheetTop - innerArcSize / 4.7f
        val contentTopPadding = sheetTop - if (compact) 14.dp else 10.dp
        val welcomeShadow = Shadow(
            color = Color.Black.copy(alpha = 0.12f),
            offset = Offset(0f, 7f),
            blurRadius = 12f,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LoginHeroPattern(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetFlatTop + if (compact) 6.dp else 14.dp)
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
                    .padding(horizontal = if (compact) 24.dp else 30.dp)
                    .padding(top = contentTopPadding, bottom = if (compact) 18.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                Spacer(modifier = Modifier.height(if (compact) 0.dp else 4.dp))

                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.pcosina_logo),
                    contentDescription = "PCOSina logo",
                    modifier = Modifier.size(if (compact) 156.dp else 182.dp),
                )

                Text(
                    text = "Wellness decision support tool",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = if (compact) 14.sp else 15.sp,
                    ),
                    color = PcosinaMidnight,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Welcome Back!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 29.sp else 34.sp,
                        lineHeight = if (compact) 31.sp else 36.sp,
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
                            style = MaterialTheme.typography.bodyMedium.copy(
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
                        analytics.logEvent(if (operatorMode) "operator_login_attempt" else "login_attempt", null)
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
                        .height(if (compact) 58.dp else 64.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.4.dp,
                            )
                        }
                        Text(
                            text = when (loginState) {
                                LoginState.Loading -> if (operatorMode) "Checking access..." else "Signing in..."
                                LoginState.Success -> if (operatorMode) "Opening tools" else "Signed in"
                                else -> if (operatorMode) "Continue to operator tools" else "Sign in"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 18.sp else 20.sp,
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
                        .height(if (compact) 50.dp else 54.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(22.dp),
                            spotColor = PcosinaRoseShadow.copy(alpha = 0.16f),
                            ambientColor = PcosinaRoseShadow.copy(alpha = 0.12f),
                        ),
                ) {
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 16.sp else 18.sp,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 2.dp else 10.dp))

                TextButton(
                    onClick = onNavigateToSignUp,
                    enabled = !isLoading,
                ) {
                    Text(
                        text = "Don't have an account? Create one",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PcosinaDeepRose.copy(alpha = 0.8f),
                        ),
                    )
                }

                TextButton(
                    onClick = { operatorMode = !operatorMode },
                    enabled = !isLoading,
                    colors = ButtonDefaults.textButtonColors(contentColor = PcosinaDeepRose),
                ) {
                    Text(
                        text = if (operatorMode) {
                            "Back to personal sign in"
                        } else {
                            "Use operator access"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }

                if (operatorMode) {
                    Text(
                        text = "Authorized operator accounts only.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = PcosinaDeepRose,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 260.dp),
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
    val shape = RoundedCornerShape(16.dp)
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
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 17.sp,
                    color = PcosinaMuted,
                ),
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
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
            .height(52.dp)
            .shadow(
                elevation = 6.dp,
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
        val accent = PcosinaBlushStrong.copy(alpha = 0.92f)
        val lightStroke = Stroke(width = 4.5f, cap = StrokeCap.Round)

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
            drawLine(accent, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 4f, cap = StrokeCap.Round)
        }

        fun heart(x: Float, y: Float, scale: Float) {
            val cx = size.width * x
            val cy = size.height * y
            val path = Path().apply {
                moveTo(cx, cy + 6f * scale)
                cubicTo(cx - 16f * scale, cy - 8f * scale, cx - 26f * scale, cy + 14f * scale, cx, cy + 26f * scale)
                cubicTo(cx + 26f * scale, cy + 14f * scale, cx + 16f * scale, cy - 8f * scale, cx, cy + 6f * scale)
            }
            drawPath(path, color = accent, style = Stroke(width = 3.5f * scale, cap = StrokeCap.Round))
        }

        fun pizza(x: Float, y: Float, scale: Float) {
            val cx = size.width * x
            val cy = size.height * y
            val path = Path().apply {
                moveTo(cx, cy - 22f * scale)
                lineTo(cx - 20f * scale, cy + 22f * scale)
                lineTo(cx + 20f * scale, cy + 22f * scale)
                close()
            }
            drawPath(path, color = accent, style = Stroke(width = 4f * scale, cap = StrokeCap.Round))
            drawArc(
                color = accent,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - 22f * scale, cy - 30f * scale),
                size = Size(44f * scale, 20f * scale),
                style = Stroke(width = 4f * scale),
            )
            drawCircle(accent, 3.5f * scale, Offset(cx - 7f * scale, cy - 2f * scale))
            drawCircle(accent, 3.5f * scale, Offset(cx + 5f * scale, cy + 8f * scale))
        }

        fun burger(x: Float, y: Float, scale: Float) {
            val cx = size.width * x
            val cy = size.height * y
            drawArc(
                color = accent,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - 24f * scale, cy - 18f * scale),
                size = Size(48f * scale, 22f * scale),
                style = Stroke(width = 4f * scale),
            )
            drawLine(accent, Offset(cx - 24f * scale, cy + 4f * scale), Offset(cx + 24f * scale, cy + 4f * scale), 4f * scale, StrokeCap.Round)
            drawLine(accent, Offset(cx - 18f * scale, cy + 12f * scale), Offset(cx + 18f * scale, cy + 12f * scale), 4f * scale, StrokeCap.Round)
            drawArc(
                color = accent,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - 22f * scale, cy + 6f * scale),
                size = Size(44f * scale, 16f * scale),
                style = Stroke(width = 4f * scale),
            )
        }

        fun taco(x: Float, y: Float, scale: Float) {
            val cx = size.width * x
            val cy = size.height * y
            drawArc(
                color = accent,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - 22f * scale, cy - 10f * scale),
                size = Size(44f * scale, 32f * scale),
                style = Stroke(width = 4f * scale),
            )
            drawLine(accent, Offset(cx - 12f * scale, cy - 4f * scale), Offset(cx - 2f * scale, cy - 10f * scale), 3f * scale, StrokeCap.Round)
            drawLine(accent, Offset(cx - 1f * scale, cy - 2f * scale), Offset(cx + 10f * scale, cy - 9f * scale), 3f * scale, StrokeCap.Round)
            drawLine(accent, Offset(cx - 8f * scale, cy + 4f * scale), Offset(cx + 8f * scale, cy - 2f * scale), 3f * scale, StrokeCap.Round)
        }

        taco(0.14f, 0.54f, 0.96f)
        pizza(0.5f, 0.26f, 1.16f)
        burger(0.8f, 0.54f, 1.08f)
        pizza(0.18f, 0.16f, 0.78f)
        taco(0.86f, 0.18f, 0.76f)
        burger(0.7f, 0.14f, 0.68f)

        ring(0.22f, 0.4f, 12f)
        ring(0.9f, 0.16f, 10f)
        dot(0.08f, 0.14f, 4f)
        dot(0.34f, 0.12f, 4f)
        dot(0.61f, 0.5f, 4f)
        dot(0.73f, 0.34f, 3.5f)
        dot(0.9f, 0.42f, 4f)

        sparkle(0.12f, 0.08f, 10f)
        sparkle(0.3f, 0.47f, 9f)
        sparkle(0.64f, 0.08f, 9f)
        sparkle(0.94f, 0.26f, 8f)
        heart(0.25f, 0.64f, 0.55f)
        heart(0.63f, 0.42f, 0.42f)
        heart(0.87f, 0.08f, 0.42f)

        drawArc(
            color = accent,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(size.width * 0.03f, size.height * 0.04f),
            size = Size(size.width * 0.12f, size.height * 0.08f),
            style = lightStroke,
        )
        drawArc(
            color = accent,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(size.width * 0.86f, size.height * 0.03f),
            size = Size(size.width * 0.1f, size.height * 0.07f),
            style = lightStroke,
        )
    }
}
