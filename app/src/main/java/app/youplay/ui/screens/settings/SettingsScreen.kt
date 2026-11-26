package app.youplay.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import app.youplay.data.local.AppSettings
import app.youplay.ui.components.YpAvatar
import app.youplay.ui.components.AccountSheet
import app.youplay.ui.screens.account.AccountViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import app.youplay.ui.components.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.youplay.ui.theme.AccentTheme
import app.youplay.ui.theme.YouPlayRed
import app.youplay.util.AppIcons
import app.youplay.util.LauncherIcon

private val Qualities = listOf("auto" to "Auto (best up to 1080p)", "1080" to "1080p", "720" to "720p", "480" to "480p", "360" to "360p", "144" to "144p")
private val ShortsQualities = listOf("auto" to "Auto (network adaptive)", "1080" to "1080p", "720" to "720p", "480" to "480p", "360" to "360p", "240" to "240p")
private val ShortsPreload = listOf("auto" to "Auto (10-15 s by network)", "10" to "10 seconds", "15" to "15 seconds")
private val DlQualities = listOf("1080" to "1080p", "720" to "720p", "480" to "480p", "360" to "360p")
private val AutoMaxOpts = listOf("2160" to "4K (2160p)", "1440" to "1440p", "1080" to "1080p", "720" to "720p")
private val MobileOpts = listOf("1080" to "1080p", "720" to "720p (recommended)", "480" to "480p", "360" to "360p")
private val Speeds = listOf("0.5" to "0.5x", "0.75" to "0.75x", "1.0" to "Normal", "1.25" to "1.25x", "1.5" to "1.5x", "2.0" to "2x")
private val Regions = listOf("US" to "United States", "IN" to "India", "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia", "DE" to "Germany", "FR" to "France", "JP" to "Japan", "KR" to "South Korea", "BR" to "Brazil", "MX" to "Mexico", "ID" to "Indonesia")
private val Languages = listOf("en" to "English", "hi" to "हिन्दी", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "pt" to "Português", "ja" to "日本語", "ko" to "한국어", "id" to "Bahasa Indonesia")

private class SRow(val title: String, val hint: String?, val content: @Composable () -> Unit)
private class SSection(val title: String, val icon: ImageVector, val rows: List<SRow>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onSignIn: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    accountVm: AccountViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val account by accountVm.account.collectAsStateWithLifecycle()
    val accounts by accountVm.accounts.collectAsStateWithLifecycle()
    val busy by accountVm.busy.collectAsStateWithLifecycle()
    val accMessage by accountVm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var picker by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var launcherIcon by remember { mutableStateOf(AppIcons.current(context)) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    fun switchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) =
        SRow(title, sub) { SwitchRow(title, sub, checked, onChange) }
    fun valueRow(title: String, value: String?, hint: String? = null, onClick: () -> Unit) =
        SRow(title, listOfNotNull(value, hint).joinToString(" ")) { ValueRow(title, value, onClick) }

    val sections = listOf(
        SSection("Appearance", Icons.Filled.Palette, listOf(
            SRow("Theme", "accent colour") {
                Text("Theme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentTheme.entries.forEach { t ->
                        ThemeCard(t, selected = s.accent == t.id, amoled = s.amoled, modifier = Modifier.weight(1f)) {
                            viewModel.update { it.copy(accent = t.id) }
                        }
                    }
                }
            },
            SRow("Mode", "dark amoled black battery") {
                Text("Mode", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                    SegmentedButton(selected = !s.amoled, onClick = { viewModel.update { it.copy(amoled = false) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Dark") }
                    SegmentedButton(selected = s.amoled, onClick = { viewModel.update { it.copy(amoled = true) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("AMOLED") }
                }
                Text(
                    if (s.amoled) "Pure black backgrounds. Saves battery on OLED screens." else "Soft dark surfaces tinted to your theme.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            },
            SRow("App icon", "launcher logo classic home screen") {
                Text("App icon", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LauncherIcon.entries.forEach { icon ->
                        IconCard(icon, selected = launcherIcon == icon, modifier = Modifier.weight(1f)) {
                            if (launcherIcon != icon) {
                                runCatching { AppIcons.set(context, icon) }
                                    .onSuccess { launcherIcon = icon; viewModel.showMessage("App icon changed. Your launcher may take a moment to update.") }
                                    .onFailure { viewModel.showMessage("Couldn't change the app icon on this device.") }
                            }
                        }
                    }
                }
            },
            switchRow("Show Shorts on Home", "Hide the Shorts shelf in your feed", s.showShorts) { v -> viewModel.update { it.copy(showShorts = v) } },
        )),
        SSection("Playback", Icons.Filled.PlayCircle, listOf(
            switchRow("Autoplay next video", "Play the first Up next video when one ends", s.autoplay) { v -> viewModel.update { it.copy(autoplay = v) } },
            switchRow("Background play", "Keep audio playing when you leave the app", s.backgroundPlay) { v -> viewModel.update { it.copy(backgroundPlay = v) } },
            switchRow("Picture-in-picture", "Keep the video in a small window when you leave the app", s.autoPip) { v -> viewModel.update { it.copy(autoPip = v) } },
            switchRow("SponsorBlock", "Auto-skip sponsors, intros, outros and self-promo", s.sponsorBlock) { v -> viewModel.update { it.copy(sponsorBlock = v) } },
            switchRow("Return YouTube Dislike", "Show estimated dislike counts", s.returnDislike) { v -> viewModel.update { it.copy(returnDislike = v) } },
            valueRow("Default quality", Qualities.labelFor(s.preferredQuality)) { picker = "quality" },
            valueRow("Auto quality: maximum", AutoMaxOpts.labelFor(s.autoMax.toString())) { picker = "automax" },
            valueRow("Auto quality on mobile data", MobileOpts.labelFor(s.mobileCap.toString())) { picker = "mobilecap" },
            valueRow("Default speed", Speeds.labelFor(s.defaultSpeed.toString())) { picker = "speed" },
            switchRow("Preload next video", "Get the next video ready early (media is cached on Wi-Fi only)", s.preloadNext) { v -> viewModel.update { it.copy(preloadNext = v) } },
        )),
        SSection("Shorts", Icons.Filled.PlayCircle, listOf(
            valueRow("Shorts quality", ShortsQualities.labelFor(s.shortsQuality)) { picker = "shortsquality" },
            switchRow("Start Shorts muted", "Shorts open with sound off until you unmute", s.shortsStartMuted) { v -> viewModel.update { it.copy(shortsStartMuted = v) } },
            switchRow("Auto-scroll Shorts", "Go to the next Short when one ends instead of looping", s.shortsAutoScroll) { v -> viewModel.update { it.copy(shortsAutoScroll = v) } },
            switchRow("Preload upcoming Shorts", "Prepare the next Shorts before you swipe", s.preloadShorts) { v -> viewModel.update { it.copy(preloadShorts = v) } },
            valueRow("Shorts preload length", ShortsPreload.labelFor(s.shortsPreloadSec)) { picker = "shortspreload" },
            switchRow("Preload Shorts on Wi-Fi only", "Save mobile data: no Shorts preloading on mobile networks", s.shortsPreloadWifiOnly) { v -> viewModel.update { it.copy(shortsPreloadWifiOnly = v) } },
        )),
        SSection("Notifications", Icons.Filled.Notifications, listOf(
            switchRow("New video notifications", "Check your subscriptions in the background and notify you about new uploads", s.notifyNewVideos) { v -> viewModel.update { it.copy(notifyNewVideos = v) } },
        )),
        SSection("Downloads", Icons.Filled.Download, listOf(
            valueRow("Download quality", DlQualities.labelFor(s.downloadQuality.toString())) { picker = "dlq" },
            switchRow("Wi-Fi only", "Don't download on mobile data", s.wifiOnlyDownloads) { v -> viewModel.update { it.copy(wifiOnlyDownloads = v) } },
            valueRow("Manage downloads", null, "offline videos", onOpenDownloads),
        )),
        SSection("Content", Icons.Filled.Public, listOf(
            valueRow("Region", Regions.labelFor(s.regionCode)) { picker = "region" },
            valueRow("Language", Languages.labelFor(s.language)) { picker = "language" },
            switchRow("Restricted mode", "Hide potentially mature content", s.restrictedMode) { v -> viewModel.update { it.copy(restrictedMode = v) } },
        )),
        SSection("Storage", Icons.Filled.Storage, listOf(
            valueRow("Clear cache", app.youplay.data.cache.formatBytes(cacheSize)) { confirm = "cache" },
            SRow("Auto-clean", "cache every 6 hours low space") {
                ListItem(
                    headlineContent = { Text("Auto-clean") },
                    supportingContent = { Text("Every 6 hours: removes cached data older than 6 hours, and frees media and images when the phone is low on space") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            },
        )),
        SSection("History & privacy", Icons.Filled.Lock, listOf(
            valueRow("Clear watch history", "Removes it from this device", "history") { confirm = "history" },
            valueRow("Clear search history", "Removes your recent searches", "history") { confirm = "search" },
        )),
        SSection("About", Icons.Filled.Info, listOf(
            SRow("YouPlay", "version kotlin compose") {
                ListItem(
                    headlineContent = { Text("YouPlay ${app.youplay.BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Native Kotlin · Jetpack Compose · InnerTubeX · Media3") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            },
            valueRow("Reset all settings", "Back to defaults. Your account and library are not affected.") { confirm = "reset" },
        )),
    )
    val q = query.trim()
    val shown = sections.mapNotNull { sec ->
        val rows = if (q.isEmpty()) sec.rows else sec.rows.filter { it.title.contains(q, true) || (it.hint?.contains(q, true) == true) || sec.title.contains(q, true) }
        if (rows.isEmpty()) null else SSection(sec.title, sec.icon, rows)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // ---- account ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).clickable { sheet = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YpAvatar(if (account.signedIn) account.avatarUrl else null, if (account.signedIn) account.name ?: "?" else "Guest", 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (account.signedIn) account.name ?: "Signed in" else "Guest", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            account.signedIn && accounts.size > 1 -> "${accounts.size} accounts · tap to switch"
                            account.signedIn -> listOfNotNull(account.handle, account.email).joinToString(" · ").ifBlank { "Manage account" }
                            else -> "Sign in to sync your library"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // ---- search ----
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (shown.isEmpty()) Text(
                "No settings match \"$q\".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            shown.forEach { sec ->
                Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(sec.icon, null, tint = YouPlayRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(sec.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = YouPlayRed)
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                ) {
                    sec.rows.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        r.content()
                    }
                }
            }
        }
    }

    if (sheet) AccountSheet(
        accounts = accounts, activeId = account.id, busy = busy, message = accMessage,
        onDismiss = { sheet = false; accountVm.dismiss() }, onSwitch = accountVm::switchTo, onAdd = onSignIn,
        onSync = accountVm::pull, onSignOut = accountVm::signOut, onSignOutAll = accountVm::signOutAll,
    )

    confirm?.let { kind ->
        val (title, text, action) = when (kind) {
            "history" -> Triple("Clear watch history?", "This removes the watch history stored on this device, including resume positions.") { viewModel.clearWatchHistory() }
            "search" -> Triple("Clear search history?", "Your recent searches will be removed.") { viewModel.clearSearchHistory() }
            "cache" -> Triple("Clear cache?", "Cached pages, images and media are removed. Downloads and your library are kept.") { viewModel.clearCache() }
            else -> Triple("Reset all settings?", "Every setting goes back to its default. Your account, library and downloads are not affected.") { viewModel.update { AppSettings() } }
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text(if (kind == "reset") "Reset" else "Clear", color = YouPlayRed) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    when (picker) {
        "quality" -> Picker("Default quality", Qualities, s.preferredQuality, { picker = null }) { v -> viewModel.update { it.copy(preferredQuality = v) } }
        "shortsquality" -> Picker("Shorts quality", ShortsQualities, s.shortsQuality, { picker = null }) { v -> viewModel.update { it.copy(shortsQuality = v) } }
        "shortspreload" -> Picker("Shorts preload length", ShortsPreload, s.shortsPreloadSec, { picker = null }) { v -> viewModel.update { it.copy(shortsPreloadSec = v) } }
        "automax" -> Picker("Auto quality: maximum", AutoMaxOpts, s.autoMax.toString(), { picker = null }) { v -> viewModel.update { it.copy(autoMax = v.toInt()) } }
        "mobilecap" -> Picker("Auto quality on mobile data", MobileOpts, s.mobileCap.toString(), { picker = null }) { v -> viewModel.update { it.copy(mobileCap = v.toInt()) } }
        "speed" -> Picker("Default speed", Speeds, s.defaultSpeed.toString(), { picker = null }) { v -> viewModel.update { it.copy(defaultSpeed = v.toFloat()) } }
        "dlq" -> Picker("Download quality", DlQualities, s.downloadQuality.toString(), { picker = null }) { v -> viewModel.update { it.copy(downloadQuality = v.toInt()) } }
        "region" -> Picker("Region", Regions, s.regionCode, { picker = null }) { v -> viewModel.update { it.copy(regionCode = v) } }
        "language" -> Picker("Language", Languages, s.language, { picker = null }) { v -> viewModel.update { it.copy(language = v) } }
    }
}

private fun List<Pair<String, String>>.labelFor(key: String) = firstOrNull { it.first == key }?.second ?: key

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun ValueRow(title: String, value: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A miniature of the app in the theme's colours: background, a card and an accent chip. */
@Composable
private fun ThemeCard(t: AccentTheme, selected: Boolean, amoled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (amoled) Color.Black else t.bg
    val card = if (amoled) Color(0xFF101010) else t.elev
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) t.accent else MaterialTheme.colorScheme.outline), RoundedCornerShape(14.dp))
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(26.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(t.accent))
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(card))
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(card))
            }
            if (selected) Box(
                Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(t.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = t.onAccent, modifier = Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** A launcher-icon choice: the logo on a rounded black tile, outlined in red when selected. */
@Composable
private fun IconCard(icon: LauncherIcon, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) YouPlayRed else MaterialTheme.colorScheme.outline), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(icon.preview), icon.label, Modifier.size(64.dp))
            if (selected) Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(YouPlayRed),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(icon.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun Picker(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(key); onDismiss() }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == selected, onClick = { onSelect(key); onDismiss() })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
