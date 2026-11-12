package com.streambox.app

import org.json.JSONObject
import java.net.URLEncoder

/** Afişli katalog ve Nuvio eklentileri için TMDB kimlikleri. */
data class TmdbItem(
    val id: Int,
    val type: String, // "movie" | "tv"
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String,
    val year: String?,
    val rating: Double?,
)

data class TmdbSeason(val number: Int, val name: String, val episodeCount: Int)
data class TmdbEpisode(val number: Int, val name: String, val overview: String, val still: String?)
data class TmdbDetails(val item: TmdbItem, val genres: List<String>, val seasons: List<TmdbSeason>, val runtime: Int?)

object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    fun img(path: String?, size: String = "w342") =
        if (path.isNullOrBlank() || path == "null") null else "https://image.tmdb.org/t/p/$size$path"

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val key = Settings.tmdbKey
        val q = (params + mapOf("language" to Settings.language) +
            (if (key.length <= 40) mapOf("api_key" to key) else emptyMap()))
            .entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val url = "$BASE$path?$q"
        val text = if (key.length > 40) Net.getText(url, mapOf("Authorization" to "Bearer $key")) else Net.getText(url)
        return JSONObject(text)
    }

    private fun parse(o: JSONObject, forceType: String? = null): TmdbItem? {
        val type = forceType ?: o.optString("media_type")
        if (type != "movie" && type != "tv") return null
        val title = o.optString(if (type == "movie") "title" else "name").ifBlank { return null }
        val date = o.optString(if (type == "movie") "release_date" else "first_air_date")
        return TmdbItem(
            id = o.getInt("id"), type = type, title = title,
            poster = img(o.optString("poster_path")),
            backdrop = img(o.optString("backdrop_path"), "w780"),
            overview = o.optString("overview"),
            year = date.take(4).ifBlank { null },
            rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
        )
    }

    private suspend fun list(path: String, type: String?, params: Map<String, String> = emptyMap()): List<TmdbItem> {
        val arr = get(path, params).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parse(arr.getJSONObject(it), type) }
    }

    /** Ana sayfa rafları. */
    suspend fun home(): List<Pair<String, List<TmdbItem>>> = listOf(
        "Trend filmler" to list("/trending/movie/week", "movie"),
        "Trend diziler" to list("/trending/tv/week", "tv"),
        "Popüler filmler" to list("/movie/popular", "movie"),
        "Popüler diziler" to list("/tv/popular", "tv"),
        "En iyi filmler" to list("/movie/top_rated", "movie"),
        "Türk dizileri" to list("/discover/tv", "tv", mapOf("with_original_language" to "tr", "sort_by" to "popularity.desc")),
        "Türk filmleri" to list("/discover/movie", "movie", mapOf("with_original_language" to "tr", "sort_by" to "popularity.desc")),
    ).filter { it.second.isNotEmpty() }

    suspend fun search(query: String): List<TmdbItem> = list("/search/multi", null, mapOf("query" to query))

    suspend fun details(type: String, id: Int): TmdbDetails {
        val o = get("/$type/$id")
        val item = parse(o, type)!!
        val genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } ?: emptyList()
        val seasons = o.optJSONArray("seasons")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optInt("season_number") > 0 }
                .map { TmdbSeason(it.optInt("season_number"), it.optString("name"), it.optInt("episode_count")) }
        } ?: emptyList()
        val runtime = o.optInt("runtime").takeIf { it > 0 }
        return TmdbDetails(item, genres, seasons, runtime)
    }

    suspend fun season(id: Int, number: Int): List<TmdbEpisode> {
        val arr = get("/tv/$id/season/$number").optJSONArray("episodes") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            TmdbEpisode(it.optInt("episode_number"), it.optString("name"), it.optString("overview"), img(it.optString("still_path"), "w300"))
        }
    }
}
