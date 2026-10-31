package app.youtubejoo

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/** Single shared GeckoRuntime (Gecko allows exactly one per process). */
object Gecko {
    private var rt: GeckoRuntime? = null

    fun runtime(ctx: Context): GeckoRuntime = rt ?: GeckoRuntime.create(
        ctx.applicationContext,
        GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .remoteDebuggingEnabled(false)
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
            .build()
    ).also { rt = it; apply(ctx, it) }

    /** Re-apply user settings (called on start and whenever the app is resumed). */
    fun apply(ctx: Context, runtime: GeckoRuntime) {
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
}
