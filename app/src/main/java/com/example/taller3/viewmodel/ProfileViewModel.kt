package com.example.taller3.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.data.UserProfile
import com.example.taller3.data.repository.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val repo = ProfileRepository()
    private val auth = FirebaseAuth.getInstance()

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
                repo.flowUser(uid).catch { e ->
                    _error.value = e.message
                }.collect { userProfile ->
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

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}