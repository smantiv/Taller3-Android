package com.example.taller3.data.model

import com.google.firebase.database.IgnoreExtraProperties

// El decorador @IgnoreExtraProperties es útil para que Firebase Realtime Database
// ignore campos adicionales en la base de datos que no coincidan con el modelo.
@IgnoreExtraProperties
data class UserProfile(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val online: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val photoUrl: String? = null
)