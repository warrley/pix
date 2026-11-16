package com.example.mobile.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

data class Coordinates(
    val latitude: String,
    val longitude: String
)

object DeviceLocationProvider {

    fun hasPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    fun lastKnownCoordinates(context: Context): Coordinates? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (bestLocation == null || loc.time > bestLocation.time) {
                bestLocation = loc
            }
        }

        val resolved = bestLocation ?: return null
        if (resolved.latitude !in -90.0..90.0 || resolved.longitude !in -180.0..180.0) return null
        return Coordinates(
            latitude = String.format(Locale.US, "%.8f", resolved.latitude),
            longitude = String.format(Locale.US, "%.8f", resolved.longitude)
        )
    }
}
