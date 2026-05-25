package com.pcosina.app.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.pcosina.app.ui.util.LegalAcceptance
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
    val session by authViewModel.session.collectAsState()
    val analytics = FirebaseAnalytics.getInstance(LocalContext.current)
    val context = LocalContext.current
    val currentUserUid = session.currentUserUid.orEmpty()
    var showTerms by rememberSaveable(currentUserUid) { mutableStateOf(false) }
    var legalNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var operatorAccessRequested by rememberSaveable { mutableStateOf(false) }
    var loginCompletionHandled by rememberSaveable { mutableStateOf(false) }
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
            if (BuildConfig.DEBUG) {
                Log.i(
                    "PCOSINA-Login",
                    "Google sign-in success email=${maskLoginEmail(account.email)} " +
                        "googleId=${maskLoginToken(account.id)}"
                )
            }
            authViewModel.onGoogleLogin(account.idToken)
        } catch (e: ApiException) {
            val message = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Google sign-in cancelled."
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Network issue during Google sign-in. Please try again."
                GoogleSignInStatusCodes.DEVELOPER_ERROR ->
                    "This build is not registered for Google sign-in yet. Add its signing certificate to Firebase, refresh google-services.json, then reinstall."
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Google sign-in failed. Please try again."
                else -> "Google sign-in failed (code ${e.statusCode})."
            }
            if (BuildConfig.DEBUG) {
                Log.w("PCOSINA-Login", "Google sign-in failed status=${e.statusCode}: $message")
            }
            authViewModel.onGoogleLoginFailure(message)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w("PCOSINA-Login", "Google sign-in failed unexpectedly.", e)
            }
            authViewModel.onGoogleLoginFailure("Google sign-in failed. ${e.message ?: ""}".trim())
        }
    }

    LaunchedEffect(loginState, currentUserUid) {
        if (loginState is LoginState.Success && !loginCompletionHandled) {
            if (currentUserUid.isBlank()) return@LaunchedEffect
            val acceptedForAccount = LegalAcceptance.hasAccepted(context, currentUserUid)
            if (!acceptedForAccount) {
                showTerms = true
                legalNotice = "Please review and accept the Terms of Service before continuing."
                return@LaunchedEffect
            }
            loginCompletionHandled = true
            analytics.logEvent("login_success", null)
            onLoginSuccess(operatorAccessRequested)
        }
    }

    LaunchedEffect(currentUserUid) {
        if (
            currentUserUid.isNotBlank() &&
            !LegalAcceptance.hasAccepted(context, currentUserUid) &&
            loginState !is LoginState.Loading
        ) {
            showTerms = true
            legalNotice = "Please review and accept the Terms of Service before continuing."
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
        val allowScroll = maxHeight < 720.dp || maxWidth < 350.dp
        val contentModifier = if (allowScroll) {
            Modifier
                .verticalScroll(scrollState)
                .imePadding()
        } else {
            Modifier.imePadding()
        }
        val sheetTop = maxHeight * if (compact) 0.25f else 0.28f
        val sheetFlatTop = sheetTop + if (compact) 66.dp else 78.dp
        val outerArcSize = maxWidth * if (compact) 1.5f else 1.64f
        val innerArcSize = maxWidth * if (compact) 1.34f else 1.46f
        val outerArcTop = sheetTop - outerArcSize / 5f
        val innerArcTop = sheetTop - innerArcSize / 4.7f
        val contentTopPadding = if (compact) 80.dp else 90.dp
        val taglineShadow = Shadow(
            color = Color.Black.copy(alpha = 0.12f),
            offset = Offset(0f, 4f),
            blurRadius = 8f,
        )

        Box(modifier = Modifier.fillMaxSize()) { Image(
                painter = painterResource(id = R.drawable.pcosina_auth_snacks_background),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetFlatTop + if (compact) 6.dp else 14.dp)
                    .statusBarsPadding(),
                contentScale = ContentScale.Crop,
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
                        .widthIn(max = if (compact) 260.dp else 286.dp)
                        .fillMaxWidth(if (compact) 0.78f else 0.84f),
                    contentScale = ContentScale.Fit,
                )

                Spacer(modifier = Modifier.height(if (compact) 108.dp else 126.dp))

                Image(
                    painter = painterResource(id = R.drawable.login_heart_hands),
                    contentDescription = "PCOSina",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = if (compact) 322.dp else 348.dp),
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

                Button(
                    onClick = {
                        legalNotice = null
                        operatorAccessRequested = true
                        loginCompletionHandled = false
                        analytics.logEvent("google_login_attempt", null)
                        googleLauncher.launch(googleSignInClient.signInIntent)
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
                    if (currentUserUid.isBlank()) {
                        legalNotice = "Finish Google sign-in before accepting the Terms of Service."
                        return@TermsOfServiceOverlay
                    }
                    LegalAcceptance.accept(context, currentUserUid)
                    showTerms = false
                    legalNotice = null
                    if (session.isLoggedIn && !loginCompletionHandled) {
                        loginCompletionHandled = true
                        analytics.logEvent("login_success", null)
                        onLoginSuccess(operatorAccessRequested)
                    }
                },
                onDecline = {
                    showTerms = false
                    legalNotice = "Terms must be accepted before signing in."
                    loginCompletionHandled = false
                    if (currentUserUid.isNotBlank()) {
                        authViewModel.onLogout()
                    }
                }
            )
        }
    }
}

private fun maskLoginEmail(email: String?): String {
    val clean = email?.trim().orEmpty()
    if (clean.isBlank() || "@" !in clean) return "(none)"
    val local = clean.substringBefore("@")
    val domain = clean.substringAfter("@")
    val localMask = when {
        local.length <= 2 -> "${local.firstOrNull() ?: '*'}*"
        else -> "${local.take(2)}***${local.takeLast(1)}"
    }
    return "$localMask@$domain"
}

private fun maskLoginToken(value: String?): String {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return "(none)"
    return if (clean.length <= 8) "***" else "${clean.take(4)}...${clean.takeLast(4)}"
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
        val density = LocalDensity.current

        Image(
            painter = painterResource(id = R.drawable.pcosina_auth_snacks_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetFlatTop + if (compact) 6.dp else 14.dp)
                .statusBarsPadding(),
            contentScale = ContentScale.Crop,
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

                    val legalScrollState = rememberScrollState()
                    var legalTrackHeightPx by remember { mutableStateOf(0) }
                    val minThumbHeightPx = with(density) { 42.dp.toPx() }
                    val thumbHeightPx = if (legalScrollState.maxValue > 0 && legalTrackHeightPx > 0) {
                        val contentHeightPx = legalTrackHeightPx + legalScrollState.maxValue
                        (legalTrackHeightPx * (legalTrackHeightPx / contentHeightPx.toFloat()))
                            .coerceIn(minThumbHeightPx, legalTrackHeightPx.toFloat())
                    } else {
                        legalTrackHeightPx.toFloat()
                    }
                    val thumbOffsetPx = if (
                        legalScrollState.maxValue > 0 &&
                        legalTrackHeightPx > thumbHeightPx
                    ) {
                        (legalScrollState.value / legalScrollState.maxValue.toFloat()) *
                            (legalTrackHeightPx - thumbHeightPx)
                    } else {
                        0f
                    }
                    val thumbHeight = with(density) { thumbHeightPx.toDp() }
                    val thumbOffset = with(density) { thumbOffsetPx.toDp() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = scrollMaxHeight)
                            .onSizeChanged { legalTrackHeightPx = it.height },
                    ) {
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
                                .offset(y = thumbOffset)
                                .height(thumbHeight)
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
