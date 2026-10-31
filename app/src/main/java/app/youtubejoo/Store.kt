package app.youtubejoo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local-only storage of bookmarks and history (JSON in private SharedPreferences). */
class Store(ctx: Context) {
    data class Entry(val title: String, val url: String, val time: Long)

    private val p = ctx.applicationContext.getSharedPreferences("joo_store", Context.MODE_PRIVATE)

    fun bookmarks(): MutableList<Entry> = read("bookmarks")
    fun history(): MutableList<Entry> = read("history")

    fun isBookmarked(url: String) = bookmarks().any { it.url == url }

    /** @return true if now bookmarked */
    fun toggleBookmark(title: String, url: String): Boolean {
        val l = bookmarks()
        val existing = l.indexOfFirst { it.url == url }
        return if (existing >= 0) { l.removeAt(existing); write("bookmarks", l); false }
        else { l.add(0, Entry(title.ifBlank { url }, url, System.currentTimeMillis())); write("bookmarks", l); true }
    }

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("about:")) return
        val l = history()
        l.removeAll { it.url == url }
        l.add(0, Entry(title.ifBlank { url }, url, System.currentTimeMillis()))
        while (l.size > 500) l.removeAt(l.size - 1)
        write("history", l)
    }

    fun remove(kind: String, url: String) {
        val l = read(kind); l.removeAll { it.url == url }; write(kind, l)
    }

    fun clearHistory() { p.edit().remove("history").apply() }

    private fun read(key: String): MutableList<Entry> {
        val out = mutableListOf<Entry>()
        try {
            val a = JSONArray(p.getString(key, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Entry(o.optString("t"), o.getString("u"), o.optLong("m")))
            }
        } catch (_: Exception) { }
        return out
    }

    private fun write(key: String, l: List<Entry>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url).put("m", it.time)) }
        p.edit().putString(key, a.toString()).apply()
    }
}
