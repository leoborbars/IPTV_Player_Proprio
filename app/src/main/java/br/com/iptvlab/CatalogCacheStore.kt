package br.com.iptvlab

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class CatalogSnapshot(
    val movieCategories: List<CatalogEntry>,
    val movies: List<CatalogEntry>,
    val seriesCategories: List<CatalogEntry>,
    val series: List<SeriesEntry>,
    val featuredDetails: MovieDetails?
)

/** A private, atomic catalog copy for launches without an Xtream refresh. */
class CatalogCacheStore(private val context: Context) {
    fun load(credentials: SavedCredentials): CatalogSnapshot? = runCatching {
        val root = JSONObject(String(fileFor(credentials).readFully(), Charsets.UTF_8))
        if (root.optInt("version") != 1) return@runCatching null
        CatalogSnapshot(
            movieCategories = root.getJSONArray("movieCategories").catalogEntries(),
            movies = root.getJSONArray("movies").catalogEntries(),
            seriesCategories = root.getJSONArray("seriesCategories").catalogEntries(),
            series = root.getJSONArray("series").seriesEntries(),
            featuredDetails = root.optJSONObject("featuredDetails")?.movieDetails()
        )
    }.getOrNull()

    fun save(credentials: SavedCredentials, snapshot: CatalogSnapshot) {
        val root = JSONObject()
            .put("version", 1)
            .put("movieCategories", JSONArray().apply { snapshot.movieCategories.forEach { put(it.toJson()) } })
            .put("movies", JSONArray().apply { snapshot.movies.forEach { put(it.toJson()) } })
            .put("seriesCategories", JSONArray().apply { snapshot.seriesCategories.forEach { put(it.toJson()) } })
            .put("series", JSONArray().apply { snapshot.series.forEach { put(it.toJson()) } })
            .put("featuredDetails", snapshot.featuredDetails?.toJson() ?: JSONObject.NULL)
        val file = fileFor(credentials)
        val stream = file.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun fileFor(credentials: SavedCredentials): AtomicFile {
        val key = "${credentials.server.trimEnd('/').lowercase()}\u0000${credentials.username}"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return AtomicFile(File(context.filesDir, "catalog-$hash.json"))
    }

    private fun CatalogEntry.toJson() = JSONObject()
        .put("id", id).put("title", title).put("extension", extension)
        .put("posterUrl", posterUrl).put("categoryId", categoryId)
        .put("addedAt", addedAt).put("rating", rating).put("plot", plot)

    private fun JSONArray.catalogEntries() = List(length()) { index ->
        getJSONObject(index).let { value ->
            CatalogEntry(value.getInt("id"), value.getString("title"), value.optString("extension"),
                value.textOrNull("posterUrl"), value.intOrNull("categoryId"),
                value.longOrNull("addedAt"), value.textOrNull("rating"), value.textOrNull("plot"))
        }
    }

    private fun SeriesEntry.toJson() = JSONObject()
        .put("id", id).put("title", title).put("coverUrl", coverUrl)
        .put("categoryId", categoryId).put("addedAt", addedAt)
        .put("rating", rating).put("plot", plot)

    private fun JSONArray.seriesEntries() = List(length()) { index ->
        getJSONObject(index).let { value ->
            SeriesEntry(value.getInt("id"), value.getString("title"), value.textOrNull("coverUrl"),
                value.intOrNull("categoryId"), value.longOrNull("addedAt"),
                value.textOrNull("rating"), value.textOrNull("plot"))
        }
    }

    private fun MovieDetails.toJson() = JSONObject()
        .put("title", title).put("posterUrl", posterUrl).put("backdropUrl", backdropUrl)
        .put("plot", plot).put("cast", cast).put("director", director)
        .put("genre", genre).put("year", year).put("duration", duration)
        .put("rating", rating).put("technicalVideo", technicalVideo)
        .put("technicalAudio", technicalAudio)

    private fun JSONObject.movieDetails() = MovieDetails(
        title = textOrNull("title"), posterUrl = textOrNull("posterUrl"),
        backdropUrl = textOrNull("backdropUrl"), plot = textOrNull("plot"),
        cast = textOrNull("cast"), director = textOrNull("director"),
        genre = textOrNull("genre"), year = textOrNull("year"),
        duration = textOrNull("duration"), rating = textOrNull("rating"),
        technicalVideo = textOrNull("technicalVideo"), technicalAudio = textOrNull("technicalAudio")
    )

    private fun JSONObject.textOrNull(key: String) = if (isNull(key)) null else optString(key)
    private fun JSONObject.intOrNull(key: String) = if (isNull(key)) null else optInt(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else optLong(key)
}

class CatalogSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("catalog_settings", Context.MODE_PRIVATE)

    fun refreshOnStart(): Boolean = preferences.getBoolean("refresh_on_start", false)

    fun setRefreshOnStart(enabled: Boolean) {
        preferences.edit().putBoolean("refresh_on_start", enabled).apply()
    }
}
