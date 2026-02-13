package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.analytics.FirebaseAnalytics
import com.pcosina.app.BuildConfig
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.LoginState
import com.pcosina.app.R
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.util.sampleFrameTiming
import java.util.Locale

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loginState by authViewModel.loginState.collectAsState()
    val analytics = FirebaseAnalytics.getInstance(LocalContext.current)
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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
                    "Google sign-in configuration error (DEVELOPER_ERROR). Contact support."
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
                jankThresholdMs = UiMotionTokens.FrameJankThresholdMs
            )
            Log.i(
                "LoginMotion",
                "frames=${stats.frames} avg=${"%.1f".format(Locale.ENGLISH, stats.avgFrameMs)}ms " +
                    "p95=${"%.1f".format(Locale.ENGLISH, stats.p95FrameMs)}ms " +
                    "max=${"%.1f".format(Locale.ENGLISH, stats.worstFrameMs)}ms " +
                    "jank=${stats.jankFrames}/${stats.frames}"
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PCOSINA",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            color = colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onBackground
        )
        
        Text(
            text = "Login to access your optimized plans",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                focusedLabelColor = colorScheme.primary
            )
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                focusedLabelColor = colorScheme.primary
            )
        )

        if (loginState is LoginState.Error) {
            Text(
                text = (loginState as LoginState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            if ((loginState as LoginState.Error).message.contains("verify", ignoreCase = true)) {
                TextButton(onClick = { authViewModel.resendVerification(email, password) }) {
                    Text("Resend verification email", color = colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                analytics.logEvent("login_attempt", null)
                authViewModel.onLogin(email, password)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
            enabled = loginState !is LoginState.Loading
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(color = colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Login", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = loginState !is LoginState.Loading
        ) {
            Text("Continue with Google", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        TextButton(
            onClick = onNavigateToSignUp,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Don't have an account? Sign Up", color = colorScheme.secondary)
        }
    }
}
