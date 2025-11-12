package com.example.taller3.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class UserRepository {

    private val db: FirebaseDatabase = FirebaseDatabase.getInstance("https://taller3-70cb1-default-rtdb.firebaseio.com")
    private val usersRef = db.getReference("users")

    fun flowOnline(uid: String): Flow<Boolean> = callbackFlow {
        val onlineRef = usersRef.child(uid).child("online")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.getValue(Boolean::class.java) ?: false
                trySend(isOnline)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        onlineRef.addValueEventListener(listener)
        awaitClose { onlineRef.removeEventListener(listener) }
    }

    fun flowLatLng(uid: String): Flow<LatLng?> = callbackFlow {
        val latLngRef = usersRef.child(uid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").getValue(Double::class.java)
                val lng = snapshot.child("lng").getValue(Double::class.java)

                if (lat != null && lng != null) {
                    trySend(LatLng(lat, lng))
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        latLngRef.addValueEventListener(listener)
        awaitClose { latLngRef.removeEventListener(listener) }
    }

    suspend fun setOnline(uid: String, isOnline: Boolean): Result<Unit> {
        return try {
            usersRef.child(uid).child("online").setValue(isOnline).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setLatLng(uid: String, lat: Double, lng: Double): Result<Unit> {
        return try {
            val updates = mapOf(
                "lat" to lat,
                "lng" to lng
            )
            usersRef.child(uid).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Result<Location> {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val location = withTimeoutOrNull(5000) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            }
            if (location != null) {
                Result.success(location)
            } else {
                Result.failure(Exception("No se pudo obtener la ubicación (timeout)."))
            }
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}