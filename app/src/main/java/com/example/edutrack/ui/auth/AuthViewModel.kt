package com.example.edutrack.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutrack.data.repository.AuthRepository
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val repository = AuthRepository()
    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _authState.value = AuthState.Error("Email dan password tidak boleh kosong")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.login(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login gagal")
            }
        }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _authState.value = AuthState.Error("Email dan password tidak boleh kosong")
            return
        }
        if (password != confirmPassword) {
            _authState.value = AuthState.Error("Password tidak cocok")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password minimal 6 karakter")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.register(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registrasi gagal")
            }
        }
    }
    fun loginWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Login Gagal")
                }
            }
    }
    fun resetPassword(email: String) {
        if (email.isEmpty()) {
            _authState.value = AuthState.Error("Email tidak boleh kosong")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.resetPassword(email)
            _authState.value = if (result.isSuccess) {
                AuthState.Success
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Gagal kirim email reset")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
