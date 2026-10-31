package app.youtubejoo

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** Single shared GeckoRuntime (Gecko allows exactly one per process). */
object Gecko {
    private var rt: GeckoRuntime? = null
    private const val UBO_ID = "uBlock0@raymondhill.net"
    private var ubo: WebExtension? = null
    private var adblockApplied: Boolean? = null

    fun runtime(ctx: Context): GeckoRuntime = rt ?: GeckoRuntime.create(
        ctx.applicationContext,
        GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .remoteDebuggingEnabled(false)
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
            .build()
    ).also { rt = it; apply(ctx, it); installAdBlock(ctx, it) }

    /** Re-apply user settings (called on start and whenever the app is resumed). */
    fun apply(ctx: Context, runtime: GeckoRuntime) {
        applyAdblock(ctx, runtime)
        val on = Prefs.filtering(ctx)
        val cb = runtime.settings.contentBlocking
        cb.setAntiTracking(if (on) ContentBlocking.AntiTracking.DEFAULT else ContentBlocking.AntiTracking.NONE)
        cb.setSafeBrowsing(if (on) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE)
        cb.setCookieBehavior(
            when (Prefs.cookieMode(ctx)) {
                "all" -> ContentBlocking.CookieBehavior.ACCEPT_ALL
                "third_party" -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
                "none" -> ContentBlocking.CookieBehavior.ACCEPT_NONE
                else -> ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
            }
        )
        runtime.settings.setPreferredColorScheme(
            when (Prefs.darkMode(ctx)) {
                "light" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                "system" -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
                else -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            }
        )
    }

    /** uBlock Origin, bundled as a built-in extension (unpacked into assets/ublock/ at build time). */
    private fun installAdBlock(ctx: Context, runtime: GeckoRuntime) {
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/ublock/", UBO_ID)
            .accept(
                { ext -> ubo = ext; adblockApplied = true; applyAdblock(ctx, runtime) },
                { e -> Log.e("YouTubeJoo", "uBlock Origin failed to load", e) }
            )
    }

    fun applyAdblock(ctx: Context, runtime: GeckoRuntime) {
        val ext = ubo ?: return
        val on = Prefs.adblock(ctx)
        if (adblockApplied == on) return
        adblockApplied = on
        if (on) runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
        else runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
    }
}
