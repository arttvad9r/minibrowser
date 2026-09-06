package com.artt.minibrowser.engine

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

private const val USER_NAVIGATION_CHAIN_WINDOW_MS = 10_000L
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
 * A normal web click is first checked against Android's URL resolution. Gecko is denied only after
 * Android has identified a concrete non-browser handler, or when its resolver has multiple
 * specialized non-browser handlers to offer. Ordinary web links therefore remain Gecko navigation.
 */
internal fun installExternalAppNavigationDelegate(session: GeckoSession, activity: Activity) {
    val current = session.navigationDelegate ?: return
    if (current is ExternalAppNavigationDelegate) return
    session.navigationDelegate = ExternalAppNavigationDelegate(activity, current)
}

/**
 * A direct user web navigation is eligible for a non-invasive Android resolver check. An HTTP
 * redirect is eligible only while it belongs to a short navigation chain that began with a real
 * user gesture. Script-driven/background navigation never gains this capability by itself.
 */
internal fun shouldTryExternalWebAppLink(
    targetUri: String,
    hasUserGesture: Boolean,
    isRedirect: Boolean,
    redirectFromRecentUserGesture: Boolean = false,
): Boolean {
    if (!isAllowedWebUri(targetUri)) return false
    return if (isRedirect) redirectFromRecentUserGesture else hasUserGesture
}

/** Pure filtering helper kept testable so browsers can never count as specialized handlers. */
internal fun specializedHandlerPackages(
    targetPackages: List<String>,
    genericBrowserPackages: Set<String>,
    selfPackage: String,
): List<String> = targetPackages
    .filter { it != selfPackage && it !in genericBrowserPackages }
    .distinct()

private class ExternalAppNavigationDelegate(
    private val activity: Activity,
    private val delegate: GeckoSession.NavigationDelegate,
) : GeckoSession.NavigationDelegate by delegate {
    private var userNavigationChainUntilMs = 0L

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? {
        val uri = request.uri
        val now = SystemClock.elapsedRealtime()

        if (!request.isRedirect) {
            userNavigationChainUntilMs = if (request.hasUserGesture && isAllowedWebUri(uri)) {
                now + USER_NAVIGATION_CHAIN_WINDOW_MS
            } else {
                0L
            }
        }

        val redirectFromRecentUserGesture = request.isRedirect && now <= userNavigationChainUntilMs
        val userInitiatedNavigation = request.hasUserGesture || redirectFromRecentUserGesture
        val launched = when {
            shouldTryExternalWebAppLink(
                targetUri = uri,
                hasUserGesture = request.hasUserGesture,
                isRedirect = request.isRedirect,
                redirectFromRecentUserGesture = redirectFromRecentUserGesture,
            ) -> launchSpecializedWebHandler(activity, uri)
            userInitiatedNavigation && !isAllowedWebUri(uri) -> launchCustomAppLink(activity, uri)
            else -> false
        }
        if (launched) {
            userNavigationChainUntilMs = 0L
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        return delegate.onLoadRequest(session, request)
    }
}

/**
 * Opens HTTP(S) outside the browser only when Android resolution exposes a specialized handler.
 *
 * The important distinction from the earlier implementation is that we do not call startActivity
 * speculatively for every cross-site link. We query first. If Android resolves the URL to the user's
 * browser, Gecko continues normally. If Android resolves it to a verified/preferred native app, we
 * launch that exact component. If Android presents a resolver, only non-browser candidates are put
 * in the chooser. This covers YouTube, maps, stores and bank/payment App Links without making normal
 * links disappear when no native app owns them.
 */
private fun launchSpecializedWebHandler(activity: Activity, value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val packageManager = activity.packageManager
    val browserPackages = genericBrowserPackages(packageManager) + activity.packageName
    val targetComponents = packageManager
        .queryIntentActivities(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            ComponentName(activityInfo.packageName, activityInfo.name)
        }
        .distinct()

    val specializedPackages = specializedHandlerPackages(
        targetPackages = targetComponents.map { it.packageName },
        genericBrowserPackages = browserPackages,
        selfPackage = activity.packageName,
    ).toSet()
    val specializedComponents = targetComponents.filter { it.packageName in specializedPackages }
    if (specializedComponents.isEmpty()) return false

    // Respect Android's verified/default App Link decision when it resolves to a concrete app.
    val resolvedComponent = packageManager
        .resolveActivity(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.let { ComponentName(it.packageName, it.name) }

    if (resolvedComponent != null) {
        if (resolvedComponent.packageName == activity.packageName ||
            resolvedComponent.packageName in browserPackages
        ) {
            // Android chose a browser (for example an unverified web link on Android 12+).
            return false
        }
        if (resolvedComponent in specializedComponents) {
            return startActivitySafely(activity, Intent(webIntent).setComponent(resolvedComponent))
        }
    }

    // A system resolver/default chooser is not returned by queryIntentActivities. In that case,
    // offer only the specialized native apps and never another browser or Minibrowser itself.
    val explicitIntents = specializedComponents.map { component ->
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

private fun genericBrowserPackages(packageManager: PackageManager): Set<String> = buildSet {
    addAll(queryPackages(packageManager, genericWebIntent("https://example.com/")))
    addAll(queryPackages(packageManager, genericWebIntent("http://example.com/")))
    addAll(
        queryPackages(
            packageManager,
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
        ),
    )
}

private fun genericWebIntent(value: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE)

private fun queryPackages(packageManager: PackageManager, intent: Intent): Set<String> =
    packageManager
        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName }

/**
 * Handles explicit app schemes such as tg:// and bank-specific schemes after a real user gesture,
 * including an immediate redirect chain that began with such a gesture. Internal/browser schemes
 * are never handed to Android. intent:// keeps the existing sanitized parser/fallback path.
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
