package com.pcosina.app.ui.screens

import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
    val loginState by authViewModel.loginState.collectAsState()
    val loginMessage by authViewModel.loginMessage.collectAsState()
    val analytics = FirebaseAnalytics.getInstance(LocalContext.current)
    val context = LocalContext.current
    val legalPrefs = remember(context) {
        context.getSharedPreferences("pcosina_legal", Context.MODE_PRIVATE)
    }
    var termsAccepted by rememberSaveable {
        mutableStateOf(legalPrefs.getBoolean("terms_accepted_2026_05_05", false))
    }
    var showTerms by rememberSaveable { mutableStateOf(!termsAccepted) }
    var legalNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var emailAccessOpen by rememberSaveable { mutableStateOf(false) }
    var operatorMode by rememberSaveable { mutableStateOf(false) }
    var operatorAccessRequested by rememberSaveable { mutableStateOf(false) }
    val isLoading = loginState is LoginState.Loading
    val loginErrorMessage = (loginState as? LoginState.Error)?.message
    val bannerData = when {
        !legalNotice.isNullOrBlank() -> FeedbackBannerData(
            tone = FeedbackBannerTone.Error,
            message = legalNotice.orEmpty(),
        )

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
                    "Google sign-in is not available on this build yet. Check the Firebase Google client configuration."
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
            onLoginSuccess(operatorAccessRequested)
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
        val allowScroll = emailAccessOpen || maxHeight < 720.dp || maxWidth < 350.dp
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
        val contentTopPadding = if (compact) 76.dp else 82.dp
        val taglineShadow = Shadow(
            color = Color.Black.copy(alpha = 0.12f),
            offset = Offset(0f, 4f),
            blurRadius = 8f,
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
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.login_ownership_watermark),
                    contentDescription = "Developed by Quadrant",
                    modifier = Modifier
                        .widthIn(max = 238.dp)
                        .fillMaxWidth(0.68f),
                    contentScale = ContentScale.Fit,
                )

                Spacer(modifier = Modifier.height(if (compact) 116.dp else 132.dp))

                Image(
                    painter = painterResource(id = R.drawable.login_heart_hands),
                    contentDescription = "PCOSina",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = if (compact) 322.dp else 356.dp),
                    contentScale = ContentScale.Fit,
                )

                Text(
                    text = "A wellness decision support tool",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 20.sp else 22.sp,
                        shadow = taglineShadow,
                    ),
                    color = Color.White,
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

                if (emailAccessOpen) {
                    EmailAccessPanel(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        operatorMode = operatorMode,
                        onOperatorModeChange = { operatorMode = it },
                        isLoading = isLoading,
                        onForgotPassword = {
                            legalNotice = null
                            authViewModel.sendPasswordReset(email)
                        },
                        onSubmit = {
                            legalNotice = null
                            if (!termsAccepted) {
                                showTerms = true
                                legalNotice = "Please accept the Terms of Service before continuing."
                            } else {
                                operatorAccessRequested = operatorMode
                                authViewModel.onLogin(email, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                    )
                }

                Button(
                    onClick = {
                        legalNotice = null
                        operatorAccessRequested = false
                        if (!termsAccepted) {
                            showTerms = true
                            legalNotice = "Please accept the Terms of Service before continuing."
                        } else {
                            analytics.logEvent("google_login_attempt", null)
                            googleLauncher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.72f),
                        contentColor = PcosinaBlushStrong,
                        disabledContentColor = PcosinaBlushStrong.copy(alpha = 0.72f),
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .height(if (compact) 58.dp else 60.dp)
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
                                color = PcosinaBlushStrong,
                                strokeWidth = 2.4.dp,
                            )
                        }
                        Text(
                            text = when (loginState) {
                                LoginState.Loading -> "Signing in..."
                                LoginState.Success -> "Signed in"
                                else -> "Continue with Google"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 17.sp else 18.sp,
                            ),
                        )
                    }
                }

                Text(
                    text = "By tapping Continue with Google, you agree to PCOSina's Terms of Use and Privacy Policy.",
                    modifier = Modifier.widthIn(max = 300.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                    color = PcosinaDeepRose.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            emailAccessOpen = true
                            operatorMode = false
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.textButtonColors(contentColor = PcosinaDeepRose),
                    ) {
                        Text("Use email access")
                    }
                    TextButton(
                        onClick = {
                            emailAccessOpen = true
                            operatorMode = true
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.textButtonColors(contentColor = PcosinaDeepRose),
                    ) {
                        Text("Use operator access")
                    }
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

        if (showTerms) {
            TermsOfServiceOverlay(
                onAccept = {
                    legalPrefs.edit().putBoolean("terms_accepted_2026_05_05", true).apply()
                    termsAccepted = true
                    showTerms = false
                    legalNotice = null
                },
                onDecline = {
                    showTerms = false
                    legalNotice = "Terms must be accepted before signing in."
                }
            )
        }
    }
}

@Composable
private fun EmailAccessPanel(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    operatorMode: Boolean,
    onOperatorModeChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onForgotPassword: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaBlushBorder),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onOperatorModeChange(false) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        if (!operatorMode) PcosinaBlushStrong else PcosinaBlushBorder,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (!operatorMode) PcosinaBlushSurface else Color.White,
                        contentColor = PcosinaDeepRose,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Email",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
                OutlinedButton(
                    onClick = { onOperatorModeChange(true) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        if (operatorMode) PcosinaBlushStrong else PcosinaBlushBorder,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (operatorMode) PcosinaBlushSurface else Color.White,
                        contentColor = PcosinaDeepRose,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Operator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                enabled = !isLoading,
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PcosinaMidnight,
                    unfocusedTextColor = PcosinaMidnight,
                    focusedLabelColor = PcosinaDeepRose,
                    unfocusedLabelColor = PcosinaMuted,
                    focusedBorderColor = PcosinaBlushStrong,
                    unfocusedBorderColor = PcosinaBlushBorder,
                    cursorColor = PcosinaDeepRose,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                enabled = !isLoading,
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = PcosinaDeepRose,
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PcosinaMidnight,
                    unfocusedTextColor = PcosinaMidnight,
                    focusedLabelColor = PcosinaDeepRose,
                    unfocusedLabelColor = PcosinaMuted,
                    focusedBorderColor = PcosinaBlushStrong,
                    unfocusedBorderColor = PcosinaBlushBorder,
                    cursorColor = PcosinaDeepRose,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onForgotPassword,
                    enabled = !isLoading,
                    colors = ButtonDefaults.textButtonColors(contentColor = PcosinaDeepRose),
                ) {
                    Text("Forgot password?")
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PcosinaBlushStrong,
                    contentColor = Color.White,
                    disabledContainerColor = PcosinaBlushStrong.copy(alpha = 0.58f),
                    disabledContentColor = Color.White.copy(alpha = 0.76f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    text = if (operatorMode) "Continue to operator tools" else "Sign in with email",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun TermsOfServiceOverlay(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        val compact = screenHeight < 790.dp || screenWidth < 400.dp
        val sheetTop = screenHeight * if (compact) 0.26f else 0.29f
        val sheetFlatTop = sheetTop + if (compact) 58.dp else 72.dp
        val outerArcSize = screenWidth * if (compact) 1.48f else 1.64f
        val innerArcSize = screenWidth * if (compact) 1.32f else 1.46f
        val scrollMaxHeight = screenHeight * if (compact) 0.43f else 0.47f

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
                .offset(y = sheetTop - outerArcSize / 5f)
                .background(PcosinaBlushStrong, CircleShape),
        )

        Box(
            modifier = Modifier
                .size(innerArcSize)
                .align(Alignment.TopCenter)
                .offset(y = sheetTop - innerArcSize / 4.7f)
                .background(PcosinaBlush, CircleShape),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (compact) 26.dp else 30.dp)
                .padding(top = if (compact) 48.dp else 54.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.login_ownership_watermark),
                contentDescription = "Developed by Quadrant",
                modifier = Modifier
                    .widthIn(max = 238.dp)
                    .fillMaxWidth(0.68f),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(if (compact) 22.dp else 28.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 358.dp)
                    .heightIn(max = screenHeight * if (compact) 0.74f else 0.72f),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                        ),
                        color = Color.Black
                    )
                    Text(
                        text = "Last updated on May 5, 2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaMuted
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = scrollMaxHeight),
                    ) {
                        val legalScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
                                .verticalScroll(legalScrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LegalSection(
                                title = "Summary",
                                body = "PCOSina provides meal planning support tailored for individuals managing PCOS. The app is intended for informational purposes only and does not replace professional medical advice. By using the app, you agree to these Terms."
                            )
                            LegalSection(
                                title = "Medical Disclaimer",
                                body = "PCOSina does not provide medical advice, diagnosis, or treatment. Users should consult a qualified healthcare professional before making dietary or health-related decisions."
                            )
                            LegalSection(
                                title = "Emergency Use",
                                body = "The application is not intended for emergency medical situations. If you are experiencing a medical emergency, seek immediate medical attention or contact emergency services."
                            )
                            LegalSection(
                                title = "Acceptable Use",
                                body = "You agree to use the application only for lawful purposes and in a manner that does not disrupt, damage, or impair the service."
                            )
                            LegalSection(
                                title = "User Responsibility",
                                body = "You acknowledge that all decisions regarding your health and nutrition are made at your own discretion and risk."
                            )
                            LegalSection(
                                title = "Account Security",
                                body = "You are responsible for maintaining the confidentiality of your account credentials and for all activities conducted under your account."
                            )
                            LegalSection(
                                title = "Data and Privacy",
                                body = "We collect and process user data to improve app functionality and support the objectives of this thesis. All data is kept confidential and will not be shared with third parties without user consent."
                            )
                            LegalSection(
                                title = "Termination",
                                body = "We reserve the right to suspend or terminate user access if there is misuse, abuse, or violation of these terms."
                            )
                            LegalSection(
                                title = "Changes to Terms",
                                body = "We may update these Terms and Conditions at any time. Continued use of the application means acceptance of the updated terms."
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .widthIn(min = 5.dp, max = 5.dp)
                                .background(PcosinaBlush.copy(alpha = 0.36f), RoundedCornerShape(50)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .height(if (compact) 88.dp else 108.dp)
                                .widthIn(min = 5.dp, max = 5.dp)
                                .background(PcosinaBlushStrong, RoundedCornerShape(50)),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        LegalActionButton(
                            text = "Decline",
                            filled = false,
                            onClick = onDecline,
                            modifier = Modifier.weight(1f)
                        )
                        LegalActionButton(
                            text = "Accept",
                            filled = true,
                            onClick = onAccept,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "By tapping Continue with Google, you agree to PCOSina's Terms of Use and Privacy Policy.",
                modifier = Modifier.widthIn(max = 300.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
                color = PcosinaDeepRose.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LegalSection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
            ),
            color = Color.Black
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            color = PcosinaMuted.copy(alpha = 0.82f)
        )
    }
}

@Composable
private fun LegalActionButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (filled) PcosinaBlushStrong else Color.White,
        border = BorderStroke(1.dp, PcosinaBlushStrong),
        shadowElevation = if (filled) 7.dp else 3.dp
    ) {
        val contentColor = if (filled) Color.White else PcosinaBlushStrong
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (filled) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = contentColor,
                textAlign = TextAlign.Center
            )
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
