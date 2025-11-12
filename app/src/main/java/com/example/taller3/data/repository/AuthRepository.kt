package com.example.taller3.data.repository

import com.example.taller3.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.AuthResult
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance("https://taller3-70cb1-default-rtdb.firebaseio.com")

    suspend fun login(email: String, password: String): AuthResult {
        return auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(name: String, phone: String, email: String, password: String): AuthResult {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = authResult.user ?: throw Exception("Error creando el usuario.")

        val userProfile = UserProfile(
            name = name,
            email = email,
            phone = phone
        )

        // 'Fire-and-forget': No esperamos a que la DB termine. 
        // Esto evita el bloqueo si hay problemas de conexión/reglas en la DB.
        db.getReference("users").child(firebaseUser.uid).setValue(userProfile)
        
        return authResult
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser() = auth.currentUser
}