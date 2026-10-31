package app.youtubejoo

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager

/** Thin wrapper over default SharedPreferences (keys match res/xml/preferences.xml). */
object Prefs {
    const val DEFAULT_HOME = "https://m.youtube.com"
    private const val KEY_STATE = "session_state"

    private fun sp(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)

    fun filtering(c: Context) = sp(c).getBoolean("filtering_enabled", true)
    fun adblock(c: Context) = sp(c).getBoolean("adblock_enabled", true)
    fun desktop(c: Context) = sp(c).getBoolean("desktop_mode", false)
    fun setDesktop(c: Context, v: Boolean) { sp(c).edit().putBoolean("desktop_mode", v).apply() }
    fun cookieMode(c: Context) = sp(c).getString("cookie_mode", "recommended") ?: "recommended"
    fun darkMode(c: Context) = sp(c).getString("dark_mode", "dark") ?: "dark"
    fun homepage(c: Context): String =
        sp(c).getString("homepage", DEFAULT_HOME)?.trim()?.takeIf { it.startsWith("https://") } ?: DEFAULT_HOME

    fun whitelist(c: Context): List<String> =
        (sp(c).getString("whitelist", "") ?: "")
            .split("\n", ",", " ")
            .map { it.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/') }
            .filter { it.isNotEmpty() }
            .distinct()

    fun setWhitelist(c: Context, list: List<String>) {
        sp(c).edit().putString("whitelist", list.joinToString("\n")).apply()
    }

    fun isWhitelisted(c: Context, url: String?): Boolean {
        val host = (if (url == null) null else Uri.parse(url).host)?.lowercase()?.removePrefix("www.") ?: return false
        return whitelist(c).any { host == it || host.endsWith(".$it") }
    }

    fun savedState(c: Context): String? = sp(c).getString(KEY_STATE, null)
    fun saveState(c: Context, s: String) { sp(c).edit().putString(KEY_STATE, s).apply() }
}
