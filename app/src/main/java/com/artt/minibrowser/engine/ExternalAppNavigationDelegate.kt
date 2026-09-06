package com.artt.minibrowser.engine

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*$")
private val BLOCKED_EXTERNAL_SCHEMES = setOf(
    "http",
    "https",
    "javascript",
    "data",
    "file",
    "content",
    "about",
    "chrome",
    "resource",
    "moz-extension",
    "blob",
)

/**
 * Wraps TabManager's navigation delegate instead of replacing its browser-state handling.
 * User-clicked web App Links may leave Gecko only when Android can route them to a non-browser
 * application. Ordinary HTTP(S) links therefore stay in Minibrowser.
 */
internal fun installExternalAppNavigationDelegate(session: GeckoSession, activity: Activity) {
    val current = session.navigationDelegate ?: return
    if (current is ExternalAppNavigationDelegate) return
    session.navigationDelegate = ExternalAppNavigationDelegate(activity, current)
}

private class ExternalAppNavigationDelegate(
    private val activity: Activity,
    private val delegate: GeckoSession.NavigationDelegate,
) : GeckoSession.NavigationDelegate by delegate {
    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? {
        if (request.hasUserGesture) {
            val uri = request.uri
            val launched = when {
                isAllowedWebUri(uri) -> launchWebAppLink(activity, uri)
                else -> launchCustomAppLink(activity, uri)
            }
            if (launched) return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        return delegate.onLoadRequest(session, request)
    }
}

/**
 * Android 11+ exposes exactly the browser behavior needed here: REQUIRE_NON_BROWSER succeeds only
 * when a non-browser activity can handle the URL. If only browsers are available, Gecko continues
 * the navigation normally.
 */
private fun launchWebAppLink(activity: Activity, value: String): Boolean {
    val uri = Uri.parse(value)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        return startActivitySafely(activity, intent)
    }

    return launchLegacySpecializedWebHandler(activity, intent)
}

/** Android 8-10 equivalent: remove generic browser handlers and launch only URL-specific apps. */
private fun launchLegacySpecializedWebHandler(activity: Activity, webIntent: Intent): Boolean {
    val packageManager = activity.packageManager
    val genericBrowserPackages = buildSet {
        addAll(queryPackages(packageManager, genericWebIntent("https://example.com/")))
        addAll(queryPackages(packageManager, genericWebIntent("http://example.com/")))
        addAll(
            queryPackages(
                packageManager,
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            ),
        )
        add(activity.packageName)
    }

    val candidates = packageManager
        .queryIntentActivities(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            if (activityInfo.packageName in genericBrowserPackages) return@mapNotNull null
            ComponentName(activityInfo.packageName, activityInfo.name)
        }
        .distinct()

    if (candidates.isEmpty()) return false
    val explicitIntents = candidates.map { component ->
        Intent(webIntent).setComponent(component)
    }
    val launchIntent = if (explicitIntents.size == 1) {
        explicitIntents.first()
    } else {
        Intent.createChooser(explicitIntents.first(), null).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, explicitIntents.drop(1).toTypedArray())
        }
    }
    return startActivitySafely(activity, launchIntent)
}

private fun genericWebIntent(value: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE)

private fun queryPackages(packageManager: PackageManager, intent: Intent): Set<String> =
    packageManager
        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName }

/**
 * Handles explicit app schemes such as tg:// only after a real user gesture. Internal/browser
 * schemes are never handed to Android. intent:// keeps the existing sanitized parser/fallback path.
 */
private fun launchCustomAppLink(activity: Activity, value: String): Boolean {
    val intent = if (value.startsWith("intent:", ignoreCase = true)) {
        createSafeExternalIntent(value)
    } else {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (!URI_SCHEME.matches(scheme) || scheme in BLOCKED_EXTERNAL_SCHEMES) return false
        if (uri.schemeSpecificPart.isNullOrBlank()) return false
        Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } ?: return false

    return startActivitySafely(activity, intent)
}

private fun startActivitySafely(activity: Activity, intent: Intent): Boolean = try {
    activity.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
