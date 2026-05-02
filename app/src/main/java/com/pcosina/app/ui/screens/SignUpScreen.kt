package com.pcosina.app.ui.screens

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.analytics.FirebaseAnalytics
import com.pcosina.app.R
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.SignUpState
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

@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val email by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()
    val confirmPassword by authViewModel.confirmPassword.collectAsState()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val error by authViewModel.error.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()
    val signUpState by authViewModel.signUpState.collectAsState()
    val analytics = FirebaseAnalytics.getInstance(LocalContext.current)
    val signUpErrorMessage = (signUpState as? SignUpState.Error)?.message ?: error
    val emailFieldError = signUpErrorMessage?.contains("email", ignoreCase = true) == true
    val passwordFieldError = signUpErrorMessage?.contains("password", ignoreCase = true) == true
    val confirmPasswordFieldError = signUpErrorMessage?.contains("match", ignoreCase = true) == true
    val bannerData = when {
        signUpState is SignUpState.VerificationSent -> FeedbackBannerData(
            tone = FeedbackBannerTone.Success,
            message = "Verification email sent. Confirm your email before signing in.",
        )

        !signUpErrorMessage.isNullOrBlank() -> FeedbackBannerData(
            tone = FeedbackBannerTone.Error,
            message = signUpErrorMessage.orEmpty(),
        )

        else -> null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        val scrollState = rememberScrollState()
        val compact = maxHeight < 900.dp || maxWidth < 400.dp
        val sheetTop = maxHeight * if (compact) 0.27f else 0.33f
        val sheetFlatTop = sheetTop + if (compact) 72.dp else 88.dp
        val outerArcSize = maxWidth * if (compact) 1.48f else 1.66f
        val innerArcSize = maxWidth * if (compact) 1.34f else 1.5f
        val outerArcTop = sheetTop - outerArcSize / 4.6f
        val innerArcTop = sheetTop - innerArcSize / 4.35f
        val contentTopPadding = sheetTop + if (compact) 6.dp else 20.dp
        val titleShadow = Shadow(
            color = Color.Black.copy(alpha = 0.12f),
            offset = Offset(0f, 7f),
            blurRadius = 12f,
        )
        val actionLocked = isLoading || signUpState is SignUpState.VerificationSent

        Box(modifier = Modifier.fillMaxSize()) {
            SignUpHeroPattern(
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (compact) 22.dp else 28.dp)
                    .padding(top = contentTopPadding, bottom = if (compact) 28.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.pcosina_logo),
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
                    text = "Create Account",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 34.sp else 42.sp,
                        lineHeight = if (compact) 36.sp else 44.sp,
                        shadow = titleShadow,
                    ),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Create your account first, then verify your email before finishing your planning setup.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = if (compact) 15.sp else 16.sp,
                        lineHeight = if (compact) 21.sp else 22.sp,
                    ),
                    color = PcosinaMidnight.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                )

                SignUpSetupCard(
                    compact = compact,
                    verificationSent = signUpState is SignUpState.VerificationSent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                )

                bannerData?.let { banner ->
                    AppFeedbackBanner(
                        data = banner,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                    )
                }

                SignUpInputField(
                    value = email,
                    onValueChange = {
                        authViewModel.email.value = it
                        if (signUpState != SignUpState.Idle || error != null) {
                            authViewModel.clearError()
                        }
                    },
                    placeholder = "Email Address",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    enabled = !actionLocked,
                    isError = emailFieldError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )

                HelperCopy(
                    text = if (emailFieldError) {
                        signUpErrorMessage.orEmpty()
                    } else {
                        "Verification will be sent to this email before your first sign in."
                    },
                    isError = emailFieldError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                )

                SignUpInputField(
                    value = password,
                    onValueChange = {
                        authViewModel.password.value = it
                        if (signUpState != SignUpState.Idle || error != null) {
                            authViewModel.clearError()
                        }
                    },
                    placeholder = "Password",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    enabled = !actionLocked,
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
                            enabled = !actionLocked,
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = PcosinaMuted,
                            )
                        }
                    },
                )

                HelperCopy(
                    text = if (passwordFieldError) {
                        signUpErrorMessage.orEmpty()
                    } else {
                        "Use at least 8 characters with a letter, number, and special character."
                    },
                    isError = passwordFieldError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                )

                SignUpInputField(
                    value = confirmPassword,
                    onValueChange = {
                        authViewModel.confirmPassword.value = it
                        if (signUpState != SignUpState.Idle || error != null) {
                            authViewModel.clearError()
                        }
                    },
                    placeholder = "Confirm Password",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    enabled = !actionLocked,
                    isError = confirmPasswordFieldError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                )

                HelperCopy(
                    text = if (confirmPasswordFieldError) {
                        signUpErrorMessage.orEmpty()
                    } else {
                        "Repeat the same password so your first sign in works cleanly."
                    },
                    isError = confirmPasswordFieldError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                )

                Button(
                    onClick = {
                        analytics.logEvent("sign_up_attempt", null)
                        authViewModel.onSignUp { success ->
                            if (success) {
                                analytics.logEvent("sign_up_success", null)
                            }
                        }
                    },
                    enabled = !actionLocked &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        confirmPassword.isNotBlank(),
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
                        ),
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
                            text = when (signUpState) {
                                SignUpState.Loading -> "Creating account..."
                                SignUpState.VerificationSent -> "Verification sent"
                                else -> "Create account"
                            },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 21.sp else 24.sp,
                            ),
                        )
                    }
                }

                if (signUpState is SignUpState.VerificationSent) {
                    OutlinedButton(
                        onClick = onSignUpSuccess,
                        shape = RoundedCornerShape(22.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, PcosinaBlushBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PcosinaBlushSurface,
                            contentColor = PcosinaBlushStrong,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .height(if (compact) 66.dp else 74.dp),
                    ) {
                        Text(
                            text = "Open sign in",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (compact) 2.dp else 8.dp))

                TextButton(
                    onClick = onNavigateToLogin,
                    enabled = !isLoading,
                ) {
                    Text(
                        text = "Already have an account? Sign in",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PcosinaDeepRose.copy(alpha = 0.82f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignUpSetupCard(
    compact: Boolean,
    verificationSent: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = PcosinaBlushSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, PcosinaBlushBorder),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Setup path",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (verificationSent) {
                        PcosinaBlushStrong.copy(alpha = 0.14f)
                    } else {
                        Color.White.copy(alpha = 0.94f)
                    },
                    contentColor = if (verificationSent) PcosinaBlushStrong else PcosinaDeepRose,
                ) {
                    Text(
                        text = if (verificationSent) "Email sent" else "Before profile",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            Text(
                text = "Create account -> Verify email -> Sign in -> Complete profile -> Choose goals",
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = if (compact) 18.sp else 19.sp,
                ),
                color = PcosinaMidnight.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun HelperCopy(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (isError) MaterialTheme.colorScheme.error else PcosinaDeepRose.copy(alpha = 0.72f),
        modifier = modifier,
    )
}

@Composable
private fun SignUpInputField(
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
private fun SignUpHeroPattern(modifier: Modifier = Modifier) {
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
