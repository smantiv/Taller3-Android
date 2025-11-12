package com.example.taller3.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.data.repository.UserRepository
import com.example.taller3.data.repository.UserRepository.OnlineUser
import com.example.taller3.ui.utils.circularBitmapFromUrl
import com.example.taller3.ui.utils.descriptorFromBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.LruCache
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

    // NUEVO ➤ Lista de usuarios online (para mostrar sus fotos en el mapa)
    private val _onlineUsers = MutableStateFlow<List<OnlineUser>>(emptyList())
    val onlineUsers: StateFlow<List<OnlineUser>> = _onlineUsers.asStateFlow()

    // NUEVO ➤ Cache de iconos circulares (para evitar recargar la foto cada vez)
    private val iconCache = LruCache<String, BitmapDescriptor>(128)

    init {
        auth.currentUser?.uid?.let { uid ->
            // Estado online propio
            viewModelScope.launch {
                repo.flowOnline(uid)
                    .catch { e -> _uiState.update { it.copy(error = e.message ?: "Error leyendo estado online") } }
                    .collect { value -> _uiState.update { it.copy(isOnline = value) } }
            }

            // Ubicación actual propia
            viewModelScope.launch {
                repo.flowLatLng(uid)
                    .catch { e -> _uiState.update { it.copy(error = e.message ?: "Error leyendo ubicación") } }
                    .collect { latLng -> _uiState.update { it.copy(currentLatLng = latLng) } }
            }
        }

        // NUEVO ➤ Recolectar usuarios online (hasta 100)
        viewModelScope.launch {
            repo.flowOnlineUsers(limit = 100)
                .catch { e -> _uiState.update { it.copy(error = e.message ?: "Error leyendo usuarios online") } }
                .collect { list -> _onlineUsers.value = list }
        }
    }

    fun setOnline(value: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                Log.d("HomeVM", "setOnline($value)")
                repo.setOnline(uid, value).onFailure { throw it }
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
                // Activar estado online
                repo.setOnline(uid, true).getOrThrow()

                // Obtener y guardar ubicación
                val locationResult = repo.getCurrentLocation(context)
                locationResult.getOrThrow().let { location ->
                    repo.setLatLng(uid, location.latitude, location.longitude).getOrThrow()
                }
            } catch (e: Exception) {
                // Si algo falla, revertir y notificar
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

    // 🔹 NUEVO ➤ Convierte una URL en un ícono circular para el marcador
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getUserMarkerDescriptor(
        context: Context,
        uid: String,
        photoUrl: String?
    ): Any? {
        // Si ya está en cache, lo usamos
        iconCache.get(uid)?.let { return it }

        // Si hay URL, convertir a bitmap circular
        val bmp = if (!photoUrl.isNullOrBlank()) {
            circularBitmapFromUrl(context, photoUrl, sizePx = 160, borderPx = 6)
        } else null

        val descriptor = descriptorFromBitmap(bmp)
        descriptor?.let { iconCache.put(uid, it) }

        return descriptor
    }
}