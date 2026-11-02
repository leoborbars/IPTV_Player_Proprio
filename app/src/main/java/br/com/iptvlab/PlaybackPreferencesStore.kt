package br.com.iptvlab

import android.content.Context

data class PlaybackPreferences(
    val nextPromptSeconds: Int = 10,
    val updatedAt: Long = 0L
)

/** Keeps playback preferences per Xtream profile while the NAS is unavailable. */
class PlaybackPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("playback_preferences", Context.MODE_PRIVATE)

    @Synchronized
    fun get(profileId: String): PlaybackPreferences = PlaybackPreferences(
        nextPromptSeconds = preferences.getInt("$profileId:next_prompt_seconds", 10).coerceIn(5, 300),
        updatedAt = preferences.getLong("$profileId:updated_at", 0L)
    )

    @Synchronized
    fun save(profileId: String, value: PlaybackPreferences) {
        preferences.edit()
            .putInt("$profileId:next_prompt_seconds", value.nextPromptSeconds.coerceIn(5, 300))
            .putLong("$profileId:updated_at", value.updatedAt)
            .apply()
    }

    @Synchronized
    fun merge(profileId: String, remote: PlaybackPreferences) {
        if (remote.updatedAt > get(profileId).updatedAt) save(profileId, remote)
    }
}
