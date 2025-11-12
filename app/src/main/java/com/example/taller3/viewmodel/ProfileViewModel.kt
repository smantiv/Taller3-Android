package com.example.taller3.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.data.UserProfile
import com.example.taller3.data.repository.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {

    private val repo = ProfileRepository()
    private val auth = FirebaseAuth.getInstance()
    private val storageRef = FirebaseStorage.getInstance().reference.child("profilePhotos")

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        auth.currentUser?.uid?.let { uid ->
            viewModelScope.launch {
                repo.flowUser(uid)
                    .catch { e -> _error.value = e.message }
                    .collect { userProfile ->
                        _profile.value = userProfile
                        Log.d("ProfileVM", "profile=$userProfile")
                    }
            }
        }
    }

    fun onSave(name: String, phone: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repo.updateNamePhone(uid, name, phone)
            result.onSuccess {
                _message.value = "Perfil actualizado"
            }.onFailure {
                _error.value = it.message
            }
            _isSaving.value = false
        }
    }

    fun onChangePassword(password: String, confirm: String) {
        if (password.length < 6) {
            _error.value = "La contraseña debe tener al menos 6 caracteres."
            return
        }
        if (password != confirm) {
            _error.value = "Las contraseñas no coinciden."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            val result = repo.changePassword(password)
            result.onSuccess {
                _message.value = "Contraseña actualizada"
            }.onFailure {
                when (it) {
                    is FirebaseAuthRecentLoginRequiredException ->
                        _error.value = "Esta operación es sensible. Vuelve a iniciar sesión."
                    else ->
                        _error.value = it.message
                }
            }
            _isSaving.value = false
        }
    }

    /**
     * Sube la imagen seleccionada a Firebase Storage y guarda el downloadUrl en Realtime DB.
     */
    fun onPhotoPicked(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                _isSaving.value = true

                // Subir a Storage (perfil/{uid}.jpg)
                val photoRef = storageRef.child("$uid.jpg")
                photoRef.putFile(uri).await()
                val downloadUrl = photoRef.downloadUrl.await().toString()

                // Guardar URL en DB
                val result = repo.updatePhotoUrl(uid, downloadUrl)
                result.onSuccess {
                    _message.value = "Foto actualizada"
                }.onFailure {
                    _error.value = it.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Elimina la foto de perfil (borra el campo photoUrl en DB; opcionalmente se podría borrar del Storage).
     */
    fun onRemovePhoto() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                _isSaving.value = true

                // (Opcional) también podrías borrar el archivo de Storage:
                // runCatching { storageRef.child("$uid.jpg").delete().await() }

                val result = repo.updatePhotoUrl(uid, "")
                result.onSuccess {
                    _message.value = "Foto eliminada"
                }.onFailure {
                    _error.value = it.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}
