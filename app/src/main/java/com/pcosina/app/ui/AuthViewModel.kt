package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcosina.app.data.model.Session
import com.pcosina.app.data.repository.AuthRepository
import android.util.Patterns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

sealed interface SignUpState {
    data object Idle : SignUpState
    data object Loading : SignUpState
    data object VerificationSent : SignUpState
    data class Error(val message: String) : SignUpState
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    // Auth State
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
    private val _loginMessage = MutableStateFlow<String?>(null)
    val loginMessage: StateFlow<String?> = _loginMessage.asStateFlow()

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Form State (For SignUpScreen which uses these)
    var email = MutableStateFlow("")
    var password = MutableStateFlow("")
    var confirmPassword = MutableStateFlow("")

    init {
        viewModelScope.launch {
            repository.sessionFlow.collectLatest { session ->
                _session.value = session
            }
        }
    }

    fun onLogin(emailValue: String, passwordValue: String) {
        val trimmedEmail = emailValue.trim()
        _loginMessage.value = null
        val emailError = validateEmail(trimmedEmail)
        if (emailError != null) {
            _loginState.value = LoginState.Error(emailError)
            return
        }
        if (passwordValue.isBlank()) {
            _loginState.value = LoginState.Error("Password is required")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = repository.login(trimmedEmail, passwordValue)
            if (result.isSuccess) {
                _loginState.value = LoginState.Success
                clearForm()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Login failed"
                _loginState.value = LoginState.Error(msg)
            }
        }
    }

    fun onSignUp(onResult: (Boolean) -> Unit) {
        val trimmedEmail = email.value.trim()
        val emailError = validateEmail(trimmedEmail)
        if (emailError != null) {
            _error.value = emailError
            _signUpState.value = SignUpState.Error(emailError)
            onResult(false)
            return
        }
        if (password.value != confirmPassword.value) {
            _error.value = "Passwords do not match"
            _signUpState.value = SignUpState.Error("Passwords do not match")
            onResult(false)
            return
        }
        val passwordError = validatePassword(password.value)
        if (passwordError != null) {
            _error.value = passwordError
            _signUpState.value = SignUpState.Error(passwordError)
            onResult(false)
            return
        }

        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            _isLoading.value = true
            _error.value = null
            val result = repository.signUp(trimmedEmail, password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _signUpState.value = SignUpState.VerificationSent
                onResult(true)
                clearForm()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Signup failed"
                _signUpState.value = SignUpState.Error(msg)
                _error.value = msg
                onResult(false)
            }
        }
    }

    fun resendVerification(emailValue: String, passwordValue: String) {
        val trimmedEmail = emailValue.trim()
        _loginMessage.value = null
        val emailError = validateEmail(trimmedEmail)
        if (emailError != null) {
            _loginState.value = LoginState.Error(emailError)
            return
        }
        if (passwordValue.isBlank()) {
            _loginState.value = LoginState.Error("Password is required to resend verification.")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = repository.resendVerification(trimmedEmail, passwordValue)
            if (result.isSuccess) {
                _loginState.value = LoginState.Error("Verification email sent. Please check your inbox.")
            } else {
                _loginState.value = LoginState.Error(result.exceptionOrNull()?.message ?: "Failed to send verification email")
            }
        }
    }

    fun onGoogleLogin(idToken: String?) {
        _loginMessage.value = null
        if (idToken.isNullOrBlank()) {
            _loginState.value = LoginState.Error("Google sign-in failed: missing ID token.")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = repository.loginWithGoogle(idToken)
            if (result.isSuccess) {
                _loginState.value = LoginState.Success
            } else {
                _loginState.value = LoginState.Error(result.exceptionOrNull()?.message ?: "Google sign-in failed")
            }
        }
    }

    fun onGoogleLoginFailure(message: String) {
        _loginMessage.value = null
        _loginState.value = LoginState.Error(message.ifBlank { "Google sign-in failed. Try again." })
    }

    fun sendPasswordReset(emailValue: String) {
        val trimmedEmail = emailValue.trim()
        _loginMessage.value = null
        val emailError = validateEmail(trimmedEmail)
        if (emailError != null) {
            _loginState.value = LoginState.Error(emailError)
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = repository.sendPasswordReset(trimmedEmail)
            if (result.isSuccess) {
                _loginState.value = LoginState.Idle
                _loginMessage.value = "Password reset email sent. Check your inbox."
            } else {
                _loginState.value =
                    LoginState.Error(result.exceptionOrNull()?.message ?: "Could not send password reset email.")
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            repository.logout()
            _loginState.value = LoginState.Idle
            _signUpState.value = SignUpState.Idle
        }
    }

    fun clearError() {
        _error.value = null
        _loginState.value = LoginState.Idle
        _signUpState.value = SignUpState.Idle
        _loginMessage.value = null
    }

    private fun clearForm() {
        email.value = ""
        password.value = ""
        confirmPassword.value = ""
    }

    private fun validateEmail(email: String): String? {
        if (email.isBlank()) return "Email is required"
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email"
        return null
    }

    private fun validatePassword(password: String): String? {
        if (password.isBlank()) return "Password is required"
        if (password.length < 8) return "Password must be at least 8 characters"
        if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            return "Password must include at least one letter and one number"
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            return "Password must include at least one special character"
        }
        return null
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
