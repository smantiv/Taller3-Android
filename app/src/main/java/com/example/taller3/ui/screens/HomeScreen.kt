package com.example.taller3.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.taller3.navigation.Screen
import com.example.taller3.viewmodel.AuthViewModel
import com.example.taller3.viewmodel.HomeViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val onlineUsers by homeViewModel.onlineUsers.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultLatLng = LatLng(37.4219999, -122.0840575) // Googleplex
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 14f)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            homeViewModel.enableOnlineAndSaveOneShotLocation(context)
        } else {
            homeViewModel.setOnline(false)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            homeViewModel.clearError()
        }
    }

    LaunchedEffect(uiState.currentLatLng) {
        uiState.currentLatLng?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(it, 16f)
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.zIndex(1f)
                    ) {
                        Text(if (uiState.isOnline) "Online" else "Offline")
                        Switch(
                            checked = uiState.isOnline,
                            enabled = !uiState.isLoading,
                            onCheckedChange = { checked ->
                                Log.d("SwitchClick", "checked=$checked")
                                if (checked) {
                                    if (hasLocationPermission()) {
                                        homeViewModel.enableOnlineAndSaveOneShotLocation(context)
                                    } else {
                                        launcher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                } else {
                                    homeViewModel.setOnline(false)
                                }
                            }
                        )
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menú")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Perfil") }, onClick = {
                            showMenu = false
                            navController.navigate(Screen.Profile.route)
                        })
                        DropdownMenuItem(text = { Text("Cerrar Sesión") }, onClick = {
                            authViewModel.signOut()
                        })
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false)
            ) {
                // ====== 1) Marcador del USUARIO ACTUAL (con foto si está disponible) ======
                uiState.currentLatLng?.let { myPos ->
                    val myUid = authViewModel.getCurrentUser()?.uid
                    // Busca tu entrada (si estás online y el repo la expone)
                    val meOnlineEntry = onlineUsers.firstOrNull { it.uid == myUid }
                    var myIcon: BitmapDescriptor? by remember(myUid, meOnlineEntry?.photoUrl) {
                        mutableStateOf(null)
                    }

                    LaunchedEffect(myUid, meOnlineEntry?.photoUrl) {
                        if (myUid != null) {
                            myIcon = homeViewModel.getUserMarkerDescriptor(
                                context = context,
                                uid = myUid,
                                photoUrl = meOnlineEntry?.photoUrl
                            ) as BitmapDescriptor?
                        } else {
                            myIcon = null
                        }
                    }

                    Marker(
                        state = MarkerState(position = myPos),
                        title = "Tú",
                        icon = myIcon // si es null, usa default; cuando cargue, se actualiza
                    )
                }

                // ====== 2) Marcadores de OTROS usuarios online (con su foto) ======
                val myUid = authViewModel.getCurrentUser()?.uid
                onlineUsers.forEach { u ->
                    if (u.uid != null && u.uid != myUid) {
                        val pos = LatLng(u.lat, u.lng)
                        var icon: BitmapDescriptor? by remember(u.uid, u.photoUrl) {
                            mutableStateOf(null)
                        }
                        LaunchedEffect(u.uid, u.photoUrl) {
                            icon = homeViewModel.getUserMarkerDescriptor(
                                context = context,
                                uid = u.uid,
                                photoUrl = u.photoUrl
                            ) as BitmapDescriptor?
                        }
                        Marker(
                            state = MarkerState(position = pos),
                            title = u.uid,
                            icon = icon // cuando cargue, se actualiza al ícono circular
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}