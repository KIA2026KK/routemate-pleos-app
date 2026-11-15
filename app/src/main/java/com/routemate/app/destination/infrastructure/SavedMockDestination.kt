package com.routemate.app.destination.infrastructure

import android.content.Context
import com.routemate.app.destination.domain.Destination
import org.json.JSONObject

fun savedMockDestination(context: Context): MockDestinationProvider {
    val preferences = context.getSharedPreferences("routemate_test_destination_v2", Context.MODE_PRIVATE)
    val initial = runCatching {
        val json = JSONObject(preferences.getString("selection", null) ?: error("No selection"))
        Destination(json.getString("name"), json.getDouble("latitude"), json.getDouble("longitude"))
    }.getOrDefault(ExampleDestinations.songjeong.destination)
    return MockDestinationProvider(initial) { destination ->
        val json = JSONObject().put("name", destination.name).put("latitude", destination.latitude).put("longitude", destination.longitude)
        preferences.edit().putString("selection", json.toString()).apply()
    }
}
