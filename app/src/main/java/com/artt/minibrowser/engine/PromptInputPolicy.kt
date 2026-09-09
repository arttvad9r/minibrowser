package com.artt.minibrowser.engine

import java.util.Locale

private const val MIME_TOKEN_PUNCTUATION = "!#$&^_.+-"

/**
 * Normalizes the MIME filters accepted by MiniBrowser file prompts.
 *
 * This policy is independent from Gecko/ActivityResult plumbing so a future Android Components
 * [mozilla.components.concept.engine.prompt.PromptRequest.File] adapter can preserve the same
 * accepted-type behavior before prompt ownership moves away from the raw Gecko delegate.
 */
internal fun acceptedPromptMimeTypes(mimeTypes: Array<String>): Array<String> = mimeTypes
    .map { it.trim().lowercase(Locale.ROOT) }
    .filter(::isAcceptedPromptMimeType)
    .distinct()
    .toTypedArray()
    .let { if (it.isEmpty()) arrayOf("*/*") else it }

private fun isAcceptedPromptMimeType(value: String): Boolean {
    val slash = value.indexOf('/')
    if (slash <= 0 || slash != value.lastIndexOf('/') || slash == value.lastIndex) return false
    val type = value.substring(0, slash)
    val subtype = value.substring(slash + 1)
    if (type == "*") return subtype == "*"
    return isMimeToken(type) && (subtype == "*" || isMimeToken(subtype))
}

private fun isMimeToken(value: String): Boolean = value.isNotEmpty() && value.all { char ->
    char in 'a'..'z' || char in '0'..'9' || char in MIME_TOKEN_PUNCTUATION
}
