package com.example.taller3.data
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    val uid: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val online: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val photoUrl: String? = null
)