package app.youplay.data.remote

import app.youplay.domain.ChipItem
import app.youplay.domain.VideoItem

/**
 * Pure helpers for the signed-out Home. YouTube serves an anonymous Home with no recommendations, so for guests
 * the feed is assembled from public searches. Pagination tokens are our own and start with one of the prefixes
 * below; everything else is passed through to YouTube untouched.
 */
object GuestFeed {
    /** "ypg:<n>" = the "All" feed starting at topic n. */
    const val ALL = "ypg:"
    /** "ypt:<i>" = first page of topic chip i. */
    const val TOPIC = "ypt:"
    /** "ypc:<token>" = a YouTube search continuation inside a topic chip. */
    const val CONT = "ypc:"

    private const val PER_PAGE = 2

    val TOPICS: List<Pair<String, String>> = listOf(
        "Trending" to "trending now",
        "Music" to "new music videos",
        "Gaming" to "gaming",
        "News" to "world news today",
        "Live" to "live now",
        "Movies" to "official movie trailers",
        "Sports" to "sports highlights",
        "Tech" to "latest tech reviews",
        "Podcasts" to "podcast full episode",
        "Comedy" to "comedy sketches",
        "Science" to "science explained",
        "Cooking" to "easy recipes cooking",
        "Travel" to "travel vlog",
        "Learning" to "learn something new tutorial",
    )

    fun owns(token: String?) = token != null && (token.startsWith(ALL) || token.startsWith(TOPIC) || token.startsWith(CONT))

    fun chips(): List<ChipItem> =
        listOf(ChipItem("All", null, true)) + TOPICS.mapIndexed { i, t -> ChipItem(t.first, TOPIC + i) }

    /** Queries making up the "All" page that starts at topic [from]. The first page also gets the viewer's interests. */
    fun queriesFor(from: Int, interests: List<String>): List<Query> {
        val out = ArrayList<Query>()
        if (from == 0) interests.take(2).forEach { out += Query(it, newest = true) }
        for (i in from until minOf(from + PER_PAGE, TOPICS.size)) out += Query(TOPICS[i].second, newest = false)
        return out
    }

    fun nextAll(from: Int): String? = (from + PER_PAGE).takeIf { it < TOPICS.size }?.let { ALL + it }

    data class Query(val text: String, val newest: Boolean)

    /** Round-robin merge of several result lists, dropping duplicates, Shorts and unknown-length clips. */
    fun interleave(lists: List<List<VideoItem>>, exclude: Set<String> = emptySet()): List<VideoItem> {
        val seen = HashSet<String>(exclude)
        val out = ArrayList<VideoItem>()
        var i = 0
        while (lists.any { i < it.size }) {
            for (l in lists) {
                val v = l.getOrNull(i) ?: continue
                if (!v.isShort && seen.add(v.id)) out += v
            }
            i++
        }
        return out
    }
}
