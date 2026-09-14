package com.artt.minibrowser.engine

internal enum class ContextMenuTargetKind {
    LINK,
    MEDIA,
}

internal enum class ContextMenuOperation {
    OPEN_BACKGROUND,
    OPEN_PRIVATE,
    COPY,
    SHARE,
}

/**
 * Engine-neutral description of one MiniBrowser context-menu action.
 *
 * [pairedWithLink] preserves the current wording difference for media when the same context menu
 * also contains actions for a wrapping link. [privateMode] is meaningful only for open actions.
 */
internal data class ContextMenuPolicyItem(
    val targetKind: ContextMenuTargetKind,
    val operation: ContextMenuOperation,
    val uri: String,
    val privateMode: Boolean? = null,
    val pairedWithLink: Boolean = false,
)

/**
 * Builds the exact action set currently exposed by the raw Gecko context-menu controller.
 *
 * Opening a target is restricted to MiniBrowser-approved web URIs. Copy/share intentionally remain
 * available for any non-blank target because that is existing behavior and is useful for schemes
 * such as mailto/tel. A media URI equal to the link URI is de-duplicated exactly as before.
 */
internal fun contextMenuPolicyItems(
    linkUri: String?,
    mediaUri: String?,
    isPrivate: Boolean,
    canOpenWebUri: (String) -> Boolean = ::isAllowedWebUri,
): List<ContextMenuPolicyItem> {
    val link = linkUri?.takeIf { it.isNotBlank() }
    val media = mediaUri?.takeIf { it.isNotBlank() && it != link }
    if (link == null && media == null) return emptyList()

    val items = ArrayList<ContextMenuPolicyItem>(8)

    if (link != null) {
        if (canOpenWebUri(link)) {
            items += ContextMenuPolicyItem(
                targetKind = ContextMenuTargetKind.LINK,
                operation = ContextMenuOperation.OPEN_BACKGROUND,
                uri = link,
                privateMode = isPrivate,
            )
            if (!isPrivate) {
                items += ContextMenuPolicyItem(
                    targetKind = ContextMenuTargetKind.LINK,
                    operation = ContextMenuOperation.OPEN_PRIVATE,
                    uri = link,
                    privateMode = true,
                )
            }
        }
        items += ContextMenuPolicyItem(ContextMenuTargetKind.LINK, ContextMenuOperation.COPY, link)
        items += ContextMenuPolicyItem(ContextMenuTargetKind.LINK, ContextMenuOperation.SHARE, link)
    }

    if (media != null) {
        val pairedWithLink = link != null
        if (canOpenWebUri(media)) {
            items += ContextMenuPolicyItem(
                targetKind = ContextMenuTargetKind.MEDIA,
                operation = ContextMenuOperation.OPEN_BACKGROUND,
                uri = media,
                privateMode = isPrivate,
                pairedWithLink = pairedWithLink,
            )
            if (!isPrivate) {
                items += ContextMenuPolicyItem(
                    targetKind = ContextMenuTargetKind.MEDIA,
                    operation = ContextMenuOperation.OPEN_PRIVATE,
                    uri = media,
                    privateMode = true,
                    pairedWithLink = pairedWithLink,
                )
            }
        }
        items += ContextMenuPolicyItem(
            ContextMenuTargetKind.MEDIA,
            ContextMenuOperation.COPY,
            media,
            pairedWithLink = pairedWithLink,
        )
        items += ContextMenuPolicyItem(
            ContextMenuTargetKind.MEDIA,
            ContextMenuOperation.SHARE,
            media,
            pairedWithLink = pairedWithLink,
        )
    }

    return items
}
