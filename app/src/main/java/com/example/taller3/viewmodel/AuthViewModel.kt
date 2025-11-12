package com.example.taller3.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.data.repository.AuthRepository
import com.example.taller3.navigation.Screen
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

// ¡NUEVO! Estado para la pantalla de Login
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

data class AuthState(
    val isAuthenticated: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val repository: AuthRepository = AuthRepository()

    private val _registerUiState = MutableStateFlow(RegisterUiState())
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState.asStateFlow()

    // ¡NUEVO! StateFlow para el estado de la UI de Login
    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _navigationChannel = Channel<String>()
    val navigationEvent = _navigationChannel.receiveAsFlow()

    init {
        if (repository.getCurrentUser() != null) {
            _authState.value = AuthState(isAuthenticated = true)
        }
    }

    fun register(name: String, phone: String, email: String, password: String) {
        if (name.isBlank() || phone.isBlank() || email.isBlank() || password.length < 6) {
            _registerUiState.update { it.copy(error = "Por favor, completa todos los campos correctamente (contraseña mín. 6 caracteres).") }
            return
        }
        
        viewModelScope.launch {
            try {
                _registerUiState.update { it.copy(isLoading = true) }
                repository.register(name, phone, email, password)
                _authState.update { it.copy(isAuthenticated = true) }
                _navigationChannel.send(Screen.Home.route)
            } catch (e: FirebaseAuthUserCollisionException) {
                _registerUiState.update { it.copy(error = "El correo ya está en uso") }
            } catch (e: Exception) {
                _registerUiState.update { it.copy(error = "No se pudo guardar el perfil. Intenta de nuevo.") }
            } finally {
                _registerUiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _loginUiState.update { it.copy(isLoading = true) } // Inicia carga
                repository.login(email, password)
                _authState.update { it.copy(isAuthenticated = true) }
                _navigationChannel.send(Screen.Home.route)
            } catch (e: FirebaseAuthInvalidUserException) {
                _loginUiState.update { it.copy(error = "El usuario no existe.") } // Error específico
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _loginUiState.update { it.copy(error = "Credenciales incorrectas.") } // Error específico
            } catch (e: Exception) {
                _loginUiState.update { it.copy(error = "Error inesperado: ${e.message}") } // Error genérico
            } finally {
                _loginUiState.update { it.copy(isLoading = false) } // Finaliza carga
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _authState.value = AuthState(isAuthenticated = false)
    }
    
    fun clearError() {
        _registerUiState.update { it.copy(error = null) }
        _loginUiState.update { it.copy(error = null) } // Limpia ambos errores
    }

    fun getCurrentUser() = repository.getCurrentUser()
}