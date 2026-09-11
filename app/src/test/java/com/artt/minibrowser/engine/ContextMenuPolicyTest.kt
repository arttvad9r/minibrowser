package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ContextMenuPolicyTest {
    private val webOnly: (String) -> Boolean = { uri -> uri.startsWith("https://") }

    @Test
    fun normalWebLinkKeepsBackgroundPrivateCopyShareOrder() {
        assertEquals(
            listOf(
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.OPEN_BACKGROUND,
                    "https://example.test/page",
                    privateMode = false,
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.OPEN_PRIVATE,
                    "https://example.test/page",
                    privateMode = true,
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.COPY,
                    "https://example.test/page",
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.SHARE,
                    "https://example.test/page",
                ),
            ),
            contextMenuPolicyItems(
                linkUri = "https://example.test/page",
                mediaUri = null,
                isPrivate = false,
                canOpenWebUri = webOnly,
            ),
        )
    }

    @Test
    fun privateWebLinkDoesNotOfferSecondPrivateTab() {
        assertEquals(
            listOf(
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.OPEN_BACKGROUND,
                    "https://example.test/page",
                    privateMode = true,
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.COPY,
                    "https://example.test/page",
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.SHARE,
                    "https://example.test/page",
                ),
            ),
            contextMenuPolicyItems(
                linkUri = "https://example.test/page",
                mediaUri = null,
                isPrivate = true,
                canOpenWebUri = webOnly,
            ),
        )
    }

    @Test
    fun nonWebLinkStillOffersCopyAndShareOnly() {
        assertEquals(
            listOf(
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.COPY,
                    "mailto:user@example.test",
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.LINK,
                    ContextMenuOperation.SHARE,
                    "mailto:user@example.test",
                ),
            ),
            contextMenuPolicyItems(
                linkUri = "mailto:user@example.test",
                mediaUri = null,
                isPrivate = false,
                canOpenWebUri = webOnly,
            ),
        )
    }

    @Test
    fun standaloneMediaKeepsExistingOpenCopyShareSemantics() {
        assertEquals(
            listOf(
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.MEDIA,
                    ContextMenuOperation.OPEN_BACKGROUND,
                    "https://example.test/image.png",
                    privateMode = false,
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.MEDIA,
                    ContextMenuOperation.OPEN_PRIVATE,
                    "https://example.test/image.png",
                    privateMode = true,
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.MEDIA,
                    ContextMenuOperation.COPY,
                    "https://example.test/image.png",
                ),
                ContextMenuPolicyItem(
                    ContextMenuTargetKind.MEDIA,
                    ContextMenuOperation.SHARE,
                    "https://example.test/image.png",
                ),
            ),
            contextMenuPolicyItems(
                linkUri = null,
                mediaUri = "https://example.test/image.png",
                isPrivate = false,
                canOpenWebUri = webOnly,
            ),
        )
    }

    @Test
    fun linkAndDistinctMediaKeepBothActionGroups() {
        val items = contextMenuPolicyItems(
            linkUri = "https://example.test/page",
            mediaUri = "https://cdn.example.test/image.png",
            isPrivate = false,
            canOpenWebUri = webOnly,
        )

        assertEquals(8, items.size)
        assertEquals(
            listOf(
                ContextMenuOperation.OPEN_BACKGROUND,
                ContextMenuOperation.OPEN_PRIVATE,
                ContextMenuOperation.COPY,
                ContextMenuOperation.SHARE,
            ),
            items.take(4).map { it.operation },
        )
        assertEquals(List(4) { ContextMenuTargetKind.LINK }, items.take(4).map { it.targetKind })
        assertEquals(
            listOf(
                ContextMenuOperation.OPEN_BACKGROUND,
                ContextMenuOperation.OPEN_PRIVATE,
                ContextMenuOperation.COPY,
                ContextMenuOperation.SHARE,
            ),
            items.drop(4).map { it.operation },
        )
        assertEquals(List(4) { ContextMenuTargetKind.MEDIA }, items.drop(4).map { it.targetKind })
        assertEquals(List(4) { true }, items.drop(4).map { it.pairedWithLink })
    }

    @Test
    fun identicalLinkAndMediaUriIsDeduplicated() {
        val items = contextMenuPolicyItems(
            linkUri = "https://example.test/same",
            mediaUri = "https://example.test/same",
            isPrivate = false,
            canOpenWebUri = webOnly,
        )

        assertEquals(4, items.size)
        assertEquals(List(4) { ContextMenuTargetKind.LINK }, items.map { it.targetKind })
    }
}
