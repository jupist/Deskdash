package com.desk.dashboard

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.desk.dashboard.remote.RemoteStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStoreTest {
    private fun tagged(type: String, v: JsonPrimitive) = JsonObject(mapOf(type to v))

    @Test fun sendsOnlyWhatChanged() = runBlocking {
        val sent = mutableListOf<Triple<String, JsonObject, List<String>>>()
        val store = RemoteStore("settings") { name, set, removed -> sent += Triple(name, set, removed) }
        store.load(
            JsonObject(
                mapOf(
                    "temp_unit" to tagged("s", JsonPrimitive("celsius")),
                    "top_bar" to tagged("b", JsonPrimitive(true)),
                    "night_start" to tagged("s", JsonPrimitive("22:00")),
                )
            )
        )
        assertEquals("celsius", store.data.first()[stringPreferencesKey("temp_unit")])

        store.edit {
            it[stringPreferencesKey("temp_unit")] = "fahrenheit"
            it.remove(stringPreferencesKey("night_start"))
        }
        val (name, set, removed) = sent.single()
        assertEquals("settings", name)
        assertEquals(setOf("temp_unit"), set.keys)
        assertEquals(listOf("night_start"), removed)
        // The mirror shows the change straight away.
        assertEquals(true, store.data.first()[booleanPreferencesKey("top_bar")])
        assertEquals("fahrenheit", store.data.first()[stringPreferencesKey("temp_unit")])

        // No change, nothing sent.
        store.edit { it[stringPreferencesKey("temp_unit")] = "fahrenheit" }
        assertEquals(1, sent.size)
    }

    @Test fun cachesNeverTravel() = runBlocking {
        val sent = mutableListOf<JsonObject>()
        val store = RemoteStore("settings") { _, set, _ -> sent += set }
        store.load(JsonObject(emptyMap()))
        store.edit { it[stringPreferencesKey("weather_cache")] = "{}"; it[stringPreferencesKey("weather_city")] = "Pune" }
        assertTrue("weather_cache" !in sent.single())
        assertTrue("weather_city" in sent.single())
    }
}
