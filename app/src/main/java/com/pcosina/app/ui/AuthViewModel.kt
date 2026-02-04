package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcosina.app.data.model.Session
import com.pcosina.app.data.repository.AuthRepository
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
    data object Success : SignUpState
    data class Error(val message: String) : SignUpState
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    // Auth State
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

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
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = repository.login(emailValue, passwordValue)
            if (result.isSuccess) {
                _loginState.value = LoginState.Success
                clearForm()
            } else {
                _loginState.value = LoginState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun onSignUp(onResult: (Boolean) -> Unit) {
        if (password.value != confirmPassword.value) {
            _error.value = "Passwords do not match"
            onResult(false)
            return
        }
        if (password.value.length < 6) {
            _error.value = "Password must be at least 6 characters"
            onResult(false)
            return
        }

        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            _isLoading.value = true
            _error.value = null
            val result = repository.signUp(email.value, password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _signUpState.value = SignUpState.Success
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
    }

    private fun clearForm() {
        email.value = ""
        password.value = ""
        confirmPassword.value = ""
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
