package com.example.taller3.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.data.repository.UserRepository
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val isLoading: Boolean = false,
    val isOnline: Boolean = false,
    val error: String? = null,
    val currentLatLng: LatLng? = null
)

class HomeViewModel : ViewModel() {

    private val repo = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        auth.currentUser?.uid?.let { uid ->
            viewModelScope.launch {
                repo.flowOnline(uid)
                    .catch { e -> _uiState.update { it.copy(error = e.message ?: "Error leyendo estado online") } }
                    .collect { value -> _uiState.update { it.copy(isOnline = value) } }
            }
            viewModelScope.launch {
                repo.flowLatLng(uid)
                    .catch { e -> _uiState.update { it.copy(error = e.message ?: "Error leyendo ubicación") } }
                    .collect { latLng -> _uiState.update { it.copy(currentLatLng = latLng) } }
            }
        }
    }

    fun setOnline(value: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                Log.d("HomeVM", "setOnline($value)")
                repo.setOnline(uid, value)
                    .onFailure { throw it }
            } catch (e: Exception) {
                Log.e("HomeVM", "setOnline FAIL", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun enableOnlineAndSaveOneShotLocation(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Log.d("HomeVM", "enableOnline... start")
            try {
                // Primero, intentar ponerse online
                repo.setOnline(uid, true).getOrThrow()

                // Si eso funciona, obtener ubicación
                val locationResult = repo.getCurrentLocation(context)
                locationResult.getOrThrow().let { location ->
                    // Si la ubicación se obtiene, guardarla
                    repo.setLatLng(uid, location.latitude, location.longitude).getOrThrow()
                }

            } catch (e: Exception) {
                // Si algo falla, revertir a offline y mostrar error
                Log.e("HomeVM", "enableOnlineAndSaveOneShotLocation FAIL", e)
                setOnline(false)
                _uiState.update { it.copy(error = "No se pudo obtener ubicación: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
                Log.d("HomeVM", "enableOnline... end")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}