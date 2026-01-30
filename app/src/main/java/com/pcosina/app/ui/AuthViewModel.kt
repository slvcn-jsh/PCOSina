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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    // Auth State
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Form State
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

    fun onLogin(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.login(email.value, password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                onResult(true)
                clearForm()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Login failed"
                onResult(false)
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
            _isLoading.value = true
            _error.value = null
            val result = repository.signUp(email.value, password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                // Auto-login after sign up
                onLogin(onResult)
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Signup failed"
                onResult(false)
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun clearError() {
        _error.value = null
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
