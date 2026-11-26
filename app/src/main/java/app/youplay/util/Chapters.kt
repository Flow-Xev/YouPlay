package app.youplay.util

import app.youplay.domain.Chapter

// No character classes / escaped brackets: the pattern must compile on every ICU version. Built lazily and guarded so a bad pattern can never crash the player screen.
private val TS: Regex? by lazy { runCatching { Regex("(?:^|\\s)((?:\\d{1,2}:)?\\d{1,2}:\\d{2})(?:\\s|$)") }.getOrNull() }

/** Chapters from description timestamps ("0:00 Intro"). Needs >= 3 ascending entries starting at 0:00, like YouTube. */
fun parseChapters(desc: String?): List<Chapter> {
    if (desc.isNullOrBlank()) return emptyList()
    val ts = TS ?: return emptyList()
    val out = mutableListOf<Chapter>()
    for (raw in desc.lines()) {
        val line = raw.trim()
        val m = ts.find(line) ?: continue
        val p = m.groupValues[1].split(":").mapNotNull { it.toIntOrNull() }
        if (p.size < 2) continue
        val sec = if (p.size == 3) p[0] * 3600 + p[1] * 60 + p[2] else p[0] * 60 + p[1]
        val title = line.removeRange(m.range).trim(' ', '-', '–', '—', ':', '•', '|', '(', ')', '[', ']')
        if (title.isBlank()) continue
        out += Chapter(title, sec * 1000L)
    }
    val ok = out.size >= 3 && out.first().startMs == 0L && out.zipWithNext().all { (a, b) -> b.startMs > a.startMs }
    return if (ok) out else emptyList()
}
