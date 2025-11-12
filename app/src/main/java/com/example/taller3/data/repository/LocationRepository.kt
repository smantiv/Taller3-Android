package com.example.taller3.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository {

    private var fusedLocationClient: FusedLocationProviderClient? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context): Flow<LatLng> = callbackFlow {
        val client = fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(context).also {
            fusedLocationClient = it
        }

        Log.d("LocationRepository", "LocStart: Starting location updates")

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000 // 3 seconds, as requested ~2-4s
        ).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    Log.d("LocationRepository", "LocFix: ${latLng.latitude}, ${latLng.longitude}")
                    trySend(latLng)
                }
            }
        }

        client.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        awaitClose {
            Log.d("LocationRepository", "LocStop: Stopping location updates")
            client.removeLocationUpdates(locationCallback)
        }
    }
}
