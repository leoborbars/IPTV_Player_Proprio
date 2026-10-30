package br.com.iptvlab

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small LAN-only HTTP client for the self-hosted SQLite progress API. */
class ProgressSyncClient(private val settings: ProgressSyncSettings) {
    fun fetch(profileId: String): List<WatchProgress> {
        val query = URLEncoder.encode(profileId, Charsets.UTF_8.name())
        val body = request("GET", "/api/v1/progress?profile_id=$query", null)
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index -> parse(items.optJSONObject(index)) }
    }

    fun upsert(profileId: String, item: WatchProgress) {
        val payload = JSONObject()
            .put("profile_id", profileId)
            .put("media_id", item.mediaId)
            .put("title", item.title.take(500))
            .put("poster_url", item.posterUrl)
            .put("position_ms", item.positionMs.coerceAtLeast(0))
            .put("duration_ms", item.durationMs.coerceAtLeast(0))
            .put("updated_at", item.updatedAt)
            .put("completed", item.completed)
            .put("hidden_from_continue", item.hiddenFromContinue)
        request("PUT", "/api/v1/progress", payload)
    }

    fun fetchFavorites(profileId: String): List<FavoriteEntry> {
        val query = URLEncoder.encode(profileId, Charsets.UTF_8.name())
        val body = request("GET", "/api/v1/favorites?profile_id=$query", null)
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index -> parseFavorite(items.optJSONObject(index)) }
    }

    fun upsertFavorite(profileId: String, item: FavoriteEntry) {
        val payload = JSONObject()
            .put("profile_id", profileId)
            .put("media_type", item.mediaType)
            .put("media_id", item.mediaId)
            .put("title", item.title.take(500))
            .put("poster_url", item.posterUrl)
            .put("updated_at", item.updatedAt)
            .put("active", item.active)
        request("PUT", "/api/v1/favorites", payload)
    }

    private fun request(method: String, path: String, payload: JSONObject?): String {
        val base = settings.endpoint.trim().trimEnd('/')
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 2500
        connection.readTimeout = 3500
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${settings.token}")
        if (payload != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("NAS respondeu HTTP $code")
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: JSONObject?): WatchProgress? = runCatching {
        json ?: return null
        WatchProgress(
            mediaId = json.getString("media_id"),
            title = json.optString("title", "Sem título"),
            posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            positionMs = json.optLong("position_ms").coerceAtLeast(0),
            durationMs = json.optLong("duration_ms").coerceAtLeast(0),
            updatedAt = json.optLong("updated_at").coerceAtLeast(0),
            completed = storedBoolean(json, "completed"),
            hiddenFromContinue = storedBoolean(json, "hidden_from_continue")
        )
    }.getOrNull()

    private fun parseFavorite(json: JSONObject?): FavoriteEntry? = runCatching {
        json ?: return null
        val type = json.getString("media_type")
        if (type != "movie" && type != "series") return null
        FavoriteEntry(
            mediaType = type,
            mediaId = json.getInt("media_id"),
            title = json.optString("title", "Sem título"),
            posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = json.optLong("updated_at").coerceAtLeast(0),
            active = storedBoolean(json, "active")
        )
    }.getOrNull()

    private fun storedBoolean(json: JSONObject, key: String): Boolean = when (val value = json.opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", true) || value == "1"
        else -> false
    }
}
