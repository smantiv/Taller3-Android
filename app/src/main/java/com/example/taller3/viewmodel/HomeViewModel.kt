package com.example.taller3.viewmodel

import android.content.Context
import android.location.Location
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.data.repository.LocationRepository
import com.example.taller3.data.repository.UserRepository
import com.example.taller3.data.repository.UserRepository.OnlineUser
import com.example.taller3.ui.utils.circularBitmapFromUrl
import com.example.taller3.ui.utils.descriptorFromBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
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
    val currentLatLng: LatLng? = null,
    val path: List<LatLng> = emptyList()
)

class HomeViewModel : ViewModel() {

    private val userRepo = UserRepository()
    private val locationRepo = LocationRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<OnlineUser>>(emptyList())
    val onlineUsers: StateFlow<List<OnlineUser>> = _onlineUsers.asStateFlow()

    private val iconCache = LruCache<String, BitmapDescriptor>(128)
    private var locationJob: Job? = null

    private var lastTrackedTimeMs: Long = 0

    init {
        auth.currentUser?.uid?.let { uid ->
            viewModelScope.launch {
                userRepo.flowOnline(uid)
                    .catch { e -> _uiState.update { it.copy(error = e.message ?: "Error leyendo estado online") } }
                    .collect { isOnline ->
                        _uiState.update { it.copy(isOnline = isOnline) }
                        if (!isOnline) {
                            stopLocationUpdates()
                        }
                    }
            }

            viewModelScope.launch {
                userRepo.flowLatLng(uid)
                    .catch { e -> _uiState.update { it.copy(error = e.message ?: "Error leyendo ubicación") } }
                    .collect { latLng -> _uiState.update { it.copy(currentLatLng = latLng) } }
            }
        }

        viewModelScope.launch {
            userRepo.flowOnlineUsers(limit = 100)
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
                userRepo.setOnline(uid, value).onFailure { throw it }
            } catch (e: Exception) {
                Log.e("HomeVM", "setOnline FAIL", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun enableOnlineAndStartUpdates(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                userRepo.setOnline(uid, true).getOrThrow()
                startLocationUpdates(context)
            } catch (e: Exception) {
                Log.e("HomeVM", "enableOnlineAndStartUpdates FAIL", e)
                setOnline(false)
                _uiState.update { it.copy(error = "No se pudo iniciar el seguimiento: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun startLocationUpdates(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepo.startLocationUpdates(context)
                .catch { e ->
                    _uiState.update { it.copy(error = "Location error: ${e.message}") }
                    Log.e("HomeVM", "Location updates failed", e)
                }
                .collect { latLng ->
                    // aqui guardo mi ubicacion actual
                    userRepo.setLatLng(uid, latLng.latitude, latLng.longitude)
                        .onSuccess { Log.d("HomeVM", "write lat/lng OK") }
                        .onFailure { Log.e("HomeVM", "write lat/lng FAIL", it) }

                    // aqui guardo el punto en mi recorrido
                    if (shouldAppendTrack(latLng)) {
                        userRepo.appendTrackPoint(uid, latLng)
                            .onSuccess {
                                _uiState.update { it.copy(path = it.path + latLng) }
                                Log.d("HomeVM", "append track OK, track size=${_uiState.value.path.size}")
                            }
                            .onFailure { Log.e("HomeVM", "append track FAIL", it) }
                    }
                }
        }
    }

    private fun shouldAppendTrack(newLatLng: LatLng): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTrackedTimeMs < 1000) return false // no guardar si es menos de 1 seg

        val lastLatLng = _uiState.value.path.lastOrNull() ?: run {
            lastTrackedTimeMs = now
            return true
        }

        val distance = FloatArray(1)
        Location.distanceBetween(
            lastLatLng.latitude, lastLatLng.longitude,
            newLatLng.latitude, newLatLng.longitude,
            distance
        )

        if (distance[0] < 5) return false // no guardar si es menos de 5 metros

        lastTrackedTimeMs = now
        return true
    }

    private fun stopLocationUpdates() {
        if (locationJob?.isActive == true) {
            locationJob?.cancel()
            _uiState.update { it.copy(path = emptyList()) } // borro el recorrido cuando me desconecto
            Log.d("HomeVM", "Location updates stopped. Path cleared.")
        }
        locationJob = null
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    suspend fun getUserMarkerDescriptor(
        context: Context,
        uid: String,
        photoUrl: String?
    ): BitmapDescriptor? {
        iconCache.get(uid)?.let { return it }

        val bmp = if (!photoUrl.isNullOrBlank()) {
            circularBitmapFromUrl(context, photoUrl, sizePx = 160, borderPx = 6)
        } else null

        val descriptor = descriptorFromBitmap(bmp)
        descriptor?.let { iconCache.put(uid, it) }

        return descriptor
    }
}