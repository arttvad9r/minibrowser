package com.artt.minibrowser.browser

private const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
private const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
private val LOCATION_PERMISSIONS = setOf(FINE_LOCATION, COARSE_LOCATION)

/** Android may grant approximate location (coarse) while denying fine location. */
internal fun areRequestedPermissionsSatisfied(
    requested: Set<String>,
    grants: Map<String, Boolean>,
): Boolean {
    if (requested.isEmpty()) return false

    val nonLocationGranted = (requested - LOCATION_PERMISSIONS).all { grants[it] == true }
    if (!nonLocationGranted) return false

    val locationRequested = requested.any { it in LOCATION_PERMISSIONS }
    if (!locationRequested) return true

    return grants[FINE_LOCATION] == true || grants[COARSE_LOCATION] == true
}
