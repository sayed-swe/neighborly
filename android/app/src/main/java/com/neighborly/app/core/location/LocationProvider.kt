package com.neighborly.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.neighborly.app.core.util.AppError
import com.neighborly.app.core.util.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

interface LocationProvider {
    fun hasPermission(): Boolean
    suspend fun current(): LatLng
}

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasPermission(): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(): LatLng {
        if (!hasPermission()) throw AppError.Validation("Could not get your location. Please allow location access.")
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) cont.resume(LatLng(loc.latitude, loc.longitude))
                    else client.lastLocation.addOnSuccessListener { last ->
                        if (last != null) cont.resume(LatLng(last.latitude, last.longitude))
                        else cont.resumeWithException(AppError.Validation("Could not get your location. Please allow location access."))
                    }.addOnFailureListener { cont.resumeWithException(AppError.Validation("Could not get your location.")) }
                }
                .addOnFailureListener { cont.resumeWithException(AppError.Validation("Could not get your location.")) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }
}
