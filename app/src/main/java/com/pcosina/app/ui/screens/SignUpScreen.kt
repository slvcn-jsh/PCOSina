package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.analytics.FirebaseAnalytics
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.SignUpState
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.LoadingActionButton

@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val email by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()
    val confirmPassword by authViewModel.confirmPassword.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val error by authViewModel.error.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()
    val signUpState by authViewModel.signUpState.collectAsState()
    val analytics = FirebaseAnalytics.getInstance(LocalContext.current)
    val colorScheme = MaterialTheme.colorScheme
    val signUpErrorMessage = (signUpState as? SignUpState.Error)?.message ?: error
    val emailFieldError = signUpErrorMessage?.contains("email", ignoreCase = true) == true
    val passwordFieldError = signUpErrorMessage?.contains("password", ignoreCase = true) == true
    val confirmPasswordFieldError = signUpErrorMessage?.contains("match", ignoreCase = true) == true
    val actionState = when (signUpState) {
        SignUpState.Idle -> FeedbackActionState.Idle
        SignUpState.Loading -> FeedbackActionState.Loading
        SignUpState.VerificationSent -> FeedbackActionState.Success
        is SignUpState.Error -> FeedbackActionState.Error
    }
    val bannerData = when {
        signUpState is SignUpState.VerificationSent -> FeedbackBannerData(
            tone = FeedbackBannerTone.Success,
            message = "Verification email sent. Please check your inbox and verify before logging in.",
        )
        !signUpErrorMessage.isNullOrBlank() -> FeedbackBannerData(
            tone = FeedbackBannerTone.Error,
            message = signUpErrorMessage.orEmpty(),
        )
        else -> null
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
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground,
        )
        Text(
            text = "Create your account first, then verify your email before opening your plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
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
                    text = "What happens next",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "Create account -> Verify email -> Sign in -> Complete your profile -> Choose your goal -> Build your first week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "Account setup comes before Step 1 of 6",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
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
                authViewModel.email.value = it
                if (signUpState != SignUpState.Idle || error != null) {
                    authViewModel.clearError()
                }
            },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            isError = emailFieldError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = {
                Text(
                    text = if (emailFieldError) {
                        signUpErrorMessage.orEmpty()
                    } else {
                        "We'll send verification to this email before your first login."
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
                authViewModel.password.value = it
                if (signUpState != SignUpState.Idle || error != null) {
                    authViewModel.clearError()
                }
            },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    enabled = !isLoading,
                ) {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    Icon(imageVector = image, contentDescription = null, tint = colorScheme.primary)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            isError = passwordFieldError,
            supportingText = {
                Text(
                    text = if (passwordFieldError) {
                        signUpErrorMessage.orEmpty()
                    } else {
                        "Use at least 8 characters with a letter, number, and special character."
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
            value = confirmPassword,
            onValueChange = {
                authViewModel.confirmPassword.value = it
                if (signUpState != SignUpState.Idle || error != null) {
                    authViewModel.clearError()
                }
            },
            label = { Text("Confirm Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            isError = confirmPasswordFieldError,
            supportingText = {
                if (confirmPasswordFieldError) {
                    Text(signUpErrorMessage.orEmpty())
                } else {
                    Text("Repeat the same password so you can sign in cleanly on the first try.")
                }
            },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                focusedLabelColor = colorScheme.primary,
            ),
        )

        LoadingActionButton(
            state = actionState,
            idleLabel = "Sign Up",
            loadingLabel = "Creating account",
            successLabel = "Verification sent",
            errorLabel = "Fix and retry",
            onClick = {
                analytics.logEvent("sign_up_attempt", null)
                authViewModel.onSignUp { success ->
                    if (success) {
                        analytics.logEvent("sign_up_success", null)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = signUpState !is SignUpState.VerificationSent,
        )

        TextButton(
            onClick = onNavigateToLogin,
            enabled = !isLoading,
        ) {
            Text("Already have an account? Sign in", color = colorScheme.secondary)
        }

        if (signUpState is SignUpState.VerificationSent) {
            TextButton(
                onClick = onSignUpSuccess,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Open sign in", color = colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
