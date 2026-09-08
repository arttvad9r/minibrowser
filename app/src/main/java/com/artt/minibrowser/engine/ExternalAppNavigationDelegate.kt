package com.artt.minibrowser.engine

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

private const val USER_NAVIGATION_CHAIN_WINDOW_MS = 3_000L
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
 *
 * HTTP(S) is never launched synchronously from onLoadRequest. Gecko gets its ALLOW decision first;
 * only after that callback returns do we inspect Android App Links on the next main-loop turn. This
 * avoids backgrounding the Activity before Gecko has consumed the original tap.
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

/**
 * HTTP(S) handoff is intentionally non-consuming. Only a successfully launched non-web/custom
 * scheme is denied to Gecko, which cannot load that scheme itself.
 */
internal fun shouldDenyGeckoAfterExternalLaunch(targetUri: String, launched: Boolean): Boolean =
    launched && !isAllowedWebUri(targetUri)

private class ExternalAppNavigationDelegate(
    private val activity: Activity,
    private val delegate: GeckoSession.NavigationDelegate,
) : GeckoSession.NavigationDelegate by delegate {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var userNavigationChainUntilMs = 0L
    private var navigationGeneration = 0L
    private var externallyLaunchedGeneration = -1L

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny>? {
        val uri = request.uri
        val now = SystemClock.elapsedRealtime()

        if (!request.isRedirect) {
            // Every new top-level request invalidates any delayed handoff left from the previous tap.
            navigationGeneration++
            externallyLaunchedGeneration = -1L
            userNavigationChainUntilMs = if (request.hasUserGesture && isAllowedWebUri(uri)) {
                now + USER_NAVIGATION_CHAIN_WINDOW_MS
            } else {
                0L
            }
        }

        val redirectFromRecentUserGesture = request.isRedirect && now <= userNavigationChainUntilMs
        val userInitiatedNavigation = request.hasUserGesture || redirectFromRecentUserGesture

        if (
            shouldTryExternalWebAppLink(
                targetUri = uri,
                hasUserGesture = request.hasUserGesture,
                isRedirect = request.isRedirect,
                redirectFromRecentUserGesture = redirectFromRecentUserGesture,
            )
        ) {
            scheduleSpecializedWebHandoff(uri, navigationGeneration)
            // Critical ordering: return Gecko's own decision now. Do not call PackageManager or
            // startActivity until the callback has unwound and Gecko has accepted the tap.
            return delegate.onLoadRequest(session, request)
        }

        if (userInitiatedNavigation && !isAllowedWebUri(uri)) {
            val launched = launchCustomAppLink(activity, uri)
            if (launched) {
                userNavigationChainUntilMs = 0L
                externallyLaunchedGeneration = navigationGeneration
                if (shouldDenyGeckoAfterExternalLaunch(uri, launched = true)) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }
        }

        return delegate.onLoadRequest(session, request)
    }

    private fun scheduleSpecializedWebHandoff(uri: String, generation: Long) {
        mainHandler.post {
            if (generation != navigationGeneration) return@post
            if (generation == externallyLaunchedGeneration) return@post
            if (activity.isFinishing || activity.isDestroyed) return@post

            if (launchSpecializedWebHandler(activity, uri)) {
                // Once one URL in the click/redirect chain opened a native app, suppress later
                // redirects from launching a second Activity for the same user gesture.
                externallyLaunchedGeneration = generation
                userNavigationChainUntilMs = 0L
            }
        }
    }
}

internal enum class ExternalAppRequestDecision { Pass, Deny }

/**
 * App-link handling that runs inside TabManager's own NavigationDelegate.
 *
 * This deliberately does not replace or wrap GeckoSession.navigationDelegate. Regular HTTP(S)
 * clicks stay on TabManager's original code path; this helper only schedules native-app handoff
 * after Gecko has been allowed to consume the click, or consumes a custom scheme that Gecko cannot
 * render. A short user-gesture window also covers tg:// / bank-scheme navigations triggered by an
 * immediate script or redirect after the original web tap.
 */
internal class ExternalAppRequestHandler(
    private val activity: Activity,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var userNavigationChainUntilMs = 0L
    private var navigationGeneration = 0L
    private var externallyLaunchedGeneration = -1L

    fun onLoadRequest(request: GeckoSession.NavigationDelegate.LoadRequest): ExternalAppRequestDecision {
        val uri = request.uri
        val now = SystemClock.elapsedRealtime()
        val hadRecentUserGesture = now <= userNavigationChainUntilMs
        val webUri = isAllowedWebUri(uri)

        if (!request.isRedirect) {
            val continuesCustomSchemeFromRecentGesture =
                !request.hasUserGesture && !webUri && hadRecentUserGesture
            if (!continuesCustomSchemeFromRecentGesture) {
                navigationGeneration++
                externallyLaunchedGeneration = -1L
                userNavigationChainUntilMs = if (request.hasUserGesture && webUri) {
                    now + USER_NAVIGATION_CHAIN_WINDOW_MS
                } else {
                    0L
                }
            }
        }

        val redirectFromRecentUserGesture = request.isRedirect && now <= userNavigationChainUntilMs
        val customSchemeFromRecentUserGesture =
            !request.isRedirect && !request.hasUserGesture && !webUri && now <= userNavigationChainUntilMs
        val userInitiatedNavigation =
            request.hasUserGesture || redirectFromRecentUserGesture || customSchemeFromRecentUserGesture

        if (
            shouldTryExternalWebAppLink(
                targetUri = uri,
                hasUserGesture = request.hasUserGesture,
                isRedirect = request.isRedirect,
                redirectFromRecentUserGesture = redirectFromRecentUserGesture,
            )
        ) {
            scheduleSpecializedWebHandoff(uri, navigationGeneration)
            return ExternalAppRequestDecision.Pass
        }

        if (userInitiatedNavigation && !webUri) {
            val launched = launchCustomAppLink(activity, uri)
            if (launched) {
                userNavigationChainUntilMs = 0L
                externallyLaunchedGeneration = navigationGeneration
                if (shouldDenyGeckoAfterExternalLaunch(uri, launched = true)) {
                    return ExternalAppRequestDecision.Deny
                }
            }
        }

        return ExternalAppRequestDecision.Pass
    }

    private fun scheduleSpecializedWebHandoff(uri: String, generation: Long) {
        mainHandler.post {
            if (generation != navigationGeneration) return@post
            if (generation == externallyLaunchedGeneration) return@post
            if (activity.isFinishing || activity.isDestroyed) return@post

            if (launchSpecializedWebHandler(activity, uri)) {
                externallyLaunchedGeneration = generation
                userNavigationChainUntilMs = 0L
            }
        }
    }
}

/**
 * Opens HTTP(S) outside the browser only when Android resolution exposes a specialized handler.
 *
 * This function is invoked asynchronously after Gecko has already been allowed to process the tap.
 * If Android exposes only browsers, nothing happens and the web navigation continues normally. If a
 * verified/preferred native app owns the URL, that app is launched. If several specialized apps are
 * available, the chooser contains only those apps.
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
