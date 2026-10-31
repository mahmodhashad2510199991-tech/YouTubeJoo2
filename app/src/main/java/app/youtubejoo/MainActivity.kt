package app.youtubejoo

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.youtubejoo.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var store: Store
    private lateinit var runtime: GeckoRuntime
    private var session: GeckoSession? = null

    private var canGoBack = false
    private var canGoForward = false
    private var fullScreen = false
    private var loading = false
    private var currentUrl = ""
    private var currentTitle = ""
    private var failedUrl: String? = null
    private var pendingUrl: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val library = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringExtra("url")?.let { load(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = Store(this)
        runtime = Gecko.runtime(this)

        pendingUrl = intent?.data?.toString()
        setupUi()
        createSession(restore = true)
        registerNetworkCallback()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullScreen -> session?.exitFullScreen()
                    canGoBack -> session?.goBack()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { load(it) }
    }

    override fun onResume() {
        super.onResume()
        Gecko.apply(this, runtime)
    }

    override fun onDestroy() {
        networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        if (isFinishing) session?.close()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun setupUi() {
        b.urlInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                load(v.text.toString())
                v.clearFocus()
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
        b.btnBack.setOnClickListener { if (canGoBack) session?.goBack() }
        b.btnForward.setOnClickListener { if (canGoForward) session?.goForward() }
        b.btnHome.setOnClickListener { load(Prefs.homepage(this)) }
        b.btnRefresh.setOnClickListener {
            val f = failedUrl
            when {
                loading -> session?.stop()
                f != null -> session?.loadUri(f)
                else -> session?.reload()
            }
        }
        b.btnBookmark.setOnClickListener {
            if (currentUrl.isBlank()) return@setOnClickListener
            val added = store.toggleBookmark(currentTitle, currentUrl)
            Toast.makeText(this, if (added) R.string.bookmark_added else R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            updateBookmarkIcon()
        }
        b.btnMenu.setOnClickListener { showMenu() }
        updateNavButtons()
    }

    private fun showMenu() {
        val siteOff = Prefs.isWhitelisted(this, currentUrl)
        PopupMenu(this, b.btnMenu).apply {
            menu.add(0, 1, 0, R.string.menu_bookmarks)
            menu.add(0, 2, 1, R.string.menu_history)
            menu.add(0, 3, 2, if (siteOff) R.string.menu_filter_on_site else R.string.menu_filter_off_site)
            menu.add(0, 4, 3, R.string.menu_settings)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> library.launch(Intent(this@MainActivity, LibraryActivity::class.java).putExtra("mode", "bookmarks"))
                    2 -> library.launch(Intent(this@MainActivity, LibraryActivity::class.java).putExtra("mode", "history"))
                    3 -> toggleSiteFiltering()
                    4 -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
                true
            }
        }.show()
    }

    private fun toggleSiteFiltering() {
        val host = Uri.parse(currentUrl).host?.lowercase()?.removePrefix("www.") ?: return
        val list = Prefs.whitelist(this).toMutableList()
        if (!list.remove(host)) list.add(host)
        Prefs.setWhitelist(this, list)
        session?.reload()
    }

    private fun updateNavButtons() {
        b.btnBack.alpha = if (canGoBack) 1f else 0.35f
        b.btnForward.alpha = if (canGoForward) 1f else 0.35f
        b.btnRefresh.setImageResource(if (loading) R.drawable.ic_close else R.drawable.ic_refresh)
        b.btnRefresh.contentDescription = getString(if (loading) R.string.stop else R.string.refresh)
    }

    private fun updateBookmarkIcon() {
        b.btnBookmark.setImageResource(
            if (currentUrl.isNotBlank() && store.isBookmarked(currentUrl)) R.drawable.ic_star else R.drawable.ic_star_border
        )
    }

    // ----------------------------------------------------------- Browser

    private fun normalize(raw: String): String {
        val t = raw.trim()
        return when {
            t.isEmpty() -> Prefs.homepage(this)
            t.startsWith("http://") -> "https://" + t.removePrefix("http://") // never plain HTTP
            t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://m.youtube.com/results?search_query=" + Uri.encode(t)
        }
    }

    private fun load(input: String) {
        failedUrl = null
        session?.loadUri(normalize(input))
    }

    private fun createSession(restore: Boolean) {
        session?.close()
        val s = GeckoSession()
        s.contentDelegate = contentDelegate
        s.navigationDelegate = navigationDelegate
        s.progressDelegate = progressDelegate
        s.open(runtime)
        b.geckoView.setSession(s)
        session = s

        var restored = false
        val saved = Prefs.savedState(this)
        if (restore && saved != null) {
            try {
                GeckoSession.SessionState.fromString(saved)?.let { s.restoreState(it); restored = true }
            } catch (_: Exception) { }
        }
        val p = pendingUrl
        pendingUrl = null
        when {
            p != null -> s.loadUri(p)
            !restored -> s.loadUri(Prefs.homepage(this))
        }
    }

    private val contentDelegate = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            currentTitle = title ?: ""
            store.addHistory(currentTitle, currentUrl)
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) = setFullScreen(fullScreen)

        override fun onCrash(session: GeckoSession) = recover()
        override fun onKill(session: GeckoSession) = recover()
    }

    private fun recover() {
        Toast.makeText(this, R.string.crashed, Toast.LENGTH_LONG).show()
        createSession(restore = true)
    }

    private val navigationDelegate = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession, url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean
        ) {
            val shown = if (url != null && url.startsWith("data:")) failedUrl ?: "" else url ?: ""
            currentUrl = shown
            if (!b.urlInput.hasFocus()) b.urlInput.setText(shown)
            updateBookmarkIcon()
            applySiteException(session, url)
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            this@MainActivity.canGoBack = canGoBack; updateNavButtons()
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            this@MainActivity.canGoForward = canGoForward; updateNavButtons()
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            session.loadUri(uri) // single-tab: open popups/new windows in the same session
            return null
        }

        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
            failedUrl = uri
            val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
                "<body style='background:#0f0f0f;color:#fff;font-family:sans-serif;text-align:center;padding:64px 24px'>" +
                "<h2 style='color:#ff1744'>${getString(R.string.error_title)}</h2><p>${getString(R.string.error_body)}</p></body></html>"
            return GeckoResult.fromValue("data:text/html;charset=utf-8," + Uri.encode(html))
        }
    }

    private val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            loading = true; b.progress.visibility = View.VISIBLE; updateNavButtons()
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            b.progress.progress = progress
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            loading = false; b.progress.visibility = View.GONE; updateNavButtons()
        }

        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            Prefs.saveState(this@MainActivity, sessionState.toString())
        }
    }

    /** Per-site filtering exceptions driven by the whitelist setting. */
    private fun applySiteException(session: GeckoSession, url: String?) {
        if (!Prefs.filtering(this) || url == null || url.startsWith("data:")) return
        val should = Prefs.isWhitelisted(this, url)
        val ctl = runtime.contentBlockingController
        ctl.checkException(session).accept { has ->
            if (should && has != true) ctl.addException(session)
            else if (!should && has == true) ctl.removeException(session)
        }
    }

    private fun setFullScreen(on: Boolean) {
        fullScreen = on
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        WindowCompat.setDecorFitsSystemWindows(window, !on)
        if (on) ctl.hide(WindowInsetsCompat.Type.systemBars()) else ctl.show(WindowInsetsCompat.Type.systemBars())
        val vis = if (on) View.GONE else View.VISIBLE
        b.topBar.visibility = vis
        b.bottomBar.visibility = vis
        if (on) b.progress.visibility = View.GONE
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { failedUrl?.let { session?.loadUri(it); failedUrl = null } }
            }

            override fun onLost(network: Network) {
                runOnUiThread { Snackbar.make(b.root, R.string.offline, Snackbar.LENGTH_LONG).show() }
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        networkCallback = cb
    }
}
