package app.youplay.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("youplay_settings")

data class AppSettings(
    val darkTheme: Boolean = true,
    val autoplay: Boolean = true,
    val restrictedMode: Boolean = false,
    val preferredQuality: String = "auto",
    val regionCode: String = "US",
    val language: String = "en",
    val accent: String = "red",          // red | mono | ocean | violet
    val amoled: Boolean = false,         // pure black backgrounds
    val backgroundPlay: Boolean = true,
    val defaultSpeed: Float = 1f,
    val downloadQuality: Int = 720,
    val wifiOnlyDownloads: Boolean = false,
    val showShorts: Boolean = true,
    val sponsorBlock: Boolean = true,
    val returnDislike: Boolean = true,
    val autoPip: Boolean = true,
    val autoMax: Int = 1080,
    val mobileCap: Int = 720,
    val preloadNext: Boolean = true,
    val shortsQuality: String = "auto",
    val preloadShorts: Boolean = true,
    val notifyNewVideos: Boolean = true,
    val shortsPreloadSec: String = "auto",   // auto | 10 | 15
    val shortsStartMuted: Boolean = false,
    val shortsAutoScroll: Boolean = false,
    val shortsPreloadWifiOnly: Boolean = false,
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val darkTheme = booleanPreferencesKey("dark_theme")
        val autoplay = booleanPreferencesKey("autoplay")
        val restricted = booleanPreferencesKey("restricted_mode")
        val quality = stringPreferencesKey("preferred_quality")
        val region = stringPreferencesKey("region_code")
        val language = stringPreferencesKey("language")
        val accent = stringPreferencesKey("accent")
        val amoled = booleanPreferencesKey("amoled")
        val bgPlay = booleanPreferencesKey("background_play")
        val speed = floatPreferencesKey("default_speed")
        val dlQuality = intPreferencesKey("download_quality")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val showShorts = booleanPreferencesKey("show_shorts")
        val sponsorBlock = booleanPreferencesKey("sponsor_block")
        val ryd = booleanPreferencesKey("return_dislike")
        val autoPip = booleanPreferencesKey("auto_pip")
        val autoMax = intPreferencesKey("auto_max")
        val mobileCap = intPreferencesKey("mobile_cap")
        val preload = booleanPreferencesKey("preload_next")
        val shortsQuality = stringPreferencesKey("shorts_quality")
        val preloadShorts = booleanPreferencesKey("preload_shorts")
        val notifyNew = booleanPreferencesKey("notify_new_videos")
        val shortsPreloadSec = stringPreferencesKey("shorts_preload_sec")
        val shortsMuted = booleanPreferencesKey("shorts_start_muted")
        val shortsAutoScroll = booleanPreferencesKey("shorts_auto_scroll")
        val shortsPreloadWifi = booleanPreferencesKey("shorts_preload_wifi_only")
    }

    private fun read(prefs: Preferences) = AppSettings(
        darkTheme = prefs[Keys.darkTheme] ?: true,
        autoplay = prefs[Keys.autoplay] ?: true,
        restrictedMode = prefs[Keys.restricted] ?: false,
        preferredQuality = prefs[Keys.quality] ?: "auto",
        regionCode = prefs[Keys.region] ?: "US",
        language = prefs[Keys.language] ?: "en",
        accent = prefs[Keys.accent] ?: "red",
        amoled = prefs[Keys.amoled] ?: false,
        backgroundPlay = prefs[Keys.bgPlay] ?: true,
        defaultSpeed = prefs[Keys.speed] ?: 1f,
        downloadQuality = prefs[Keys.dlQuality] ?: 720,
        wifiOnlyDownloads = prefs[Keys.wifiOnly] ?: false,
        showShorts = prefs[Keys.showShorts] ?: true,
        sponsorBlock = prefs[Keys.sponsorBlock] ?: true,
        returnDislike = prefs[Keys.ryd] ?: true,
        autoPip = prefs[Keys.autoPip] ?: true,
        autoMax = prefs[Keys.autoMax] ?: 1080,
        mobileCap = prefs[Keys.mobileCap] ?: 720,
        preloadNext = prefs[Keys.preload] ?: true,
        shortsQuality = prefs[Keys.shortsQuality] ?: "auto",
        preloadShorts = prefs[Keys.preloadShorts] ?: true,
        notifyNewVideos = prefs[Keys.notifyNew] ?: true,
        shortsPreloadSec = prefs[Keys.shortsPreloadSec] ?: "auto",
        shortsStartMuted = prefs[Keys.shortsMuted] ?: false,
        shortsAutoScroll = prefs[Keys.shortsAutoScroll] ?: false,
        shortsPreloadWifiOnly = prefs[Keys.shortsPreloadWifi] ?: false,
    )

    val settings: Flow<AppSettings> = context.dataStore.data.map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.darkTheme] = next.darkTheme
            prefs[Keys.autoplay] = next.autoplay
            prefs[Keys.restricted] = next.restrictedMode
            prefs[Keys.quality] = next.preferredQuality
            prefs[Keys.region] = next.regionCode
            prefs[Keys.language] = next.language
            prefs[Keys.accent] = next.accent
            prefs[Keys.amoled] = next.amoled
            prefs[Keys.bgPlay] = next.backgroundPlay
            prefs[Keys.speed] = next.defaultSpeed
            prefs[Keys.dlQuality] = next.downloadQuality
            prefs[Keys.wifiOnly] = next.wifiOnlyDownloads
            prefs[Keys.showShorts] = next.showShorts
            prefs[Keys.sponsorBlock] = next.sponsorBlock
            prefs[Keys.ryd] = next.returnDislike
            prefs[Keys.autoPip] = next.autoPip
            prefs[Keys.autoMax] = next.autoMax
            prefs[Keys.mobileCap] = next.mobileCap
            prefs[Keys.preload] = next.preloadNext
            prefs[Keys.shortsQuality] = next.shortsQuality
            prefs[Keys.preloadShorts] = next.preloadShorts
            prefs[Keys.notifyNew] = next.notifyNewVideos
            prefs[Keys.shortsPreloadSec] = next.shortsPreloadSec
            prefs[Keys.shortsMuted] = next.shortsStartMuted
            prefs[Keys.shortsAutoScroll] = next.shortsAutoScroll
            prefs[Keys.shortsPreloadWifi] = next.shortsPreloadWifiOnly
        }
    }
}
