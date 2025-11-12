package com.example.taller3.data.repository

import android.util.Log
import com.example.taller3.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ProfileRepository {

    private val db = FirebaseDatabase.getInstance("https://taller3-70cb1-default-rtdb.firebaseio.com")
    private val usersRef = db.getReference("users")
    private val auth = FirebaseAuth.getInstance()

    fun flowUser(uid: String): Flow<UserProfile?> = callbackFlow {
        val userRef = usersRef.child(uid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(UserProfile::class.java)?.copy(uid = uid)
                trySend(profile)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        userRef.addValueEventListener(listener)
        awaitClose { userRef.removeEventListener(listener) }
    }

    suspend fun updateNamePhone(uid: String, name: String, phone: String): Result<Unit> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre no puede estar vacío."))
        }
        if (!phone.all { it.isDigit() } || phone.length !in 7..15) {
            return Result.failure(IllegalArgumentException("El teléfono debe tener entre 7 y 15 dígitos."))
        }

        return try {
            val updates = mapOf(
                "name" to name,
                "phone" to phone
            )
            usersRef.child(uid).updateChildren(updates).await()
            Log.d("ProfileRepo", "updated name/phone")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Actualiza la URL de la foto de perfil en la base de datos.
     * Si 'url' está vacío, elimina la foto (pone cadena vacía).
     */
    suspend fun updatePhotoUrl(uid: String, url: String): Result<Unit> {
        return try {
            usersRef.child(uid).child("photoUrl").setValue(url).await()
            Log.d("ProfileRepo", "updated photoUrl")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProfileRepo", "updatePhotoUrl failed", e)
            Result.failure(e)
        }
    }

    suspend fun changePassword(password: String): Result<Unit> {
        return try {
            auth.currentUser!!.updatePassword(password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
