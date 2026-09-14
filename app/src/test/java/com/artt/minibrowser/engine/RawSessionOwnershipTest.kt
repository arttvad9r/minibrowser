package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawSessionOwnershipTest {
    @Test
    fun ownedStateAllowsRawMutationAndMirroringUntilRelinquished() {
        assertTrue(RawSessionOwnership.Owned.allowsRawSessionMutation)
        assertTrue(RawSessionOwnership.Owned.mirrorsRawContent)
        assertFalse(RawSessionOwnership.Relinquished.allowsRawSessionMutation)
        assertFalse(RawSessionOwnership.Relinquished.mirrorsRawContent)
    }

    @Test
    fun rawMutationAuthorityAlsoRequiresExactSessionIdentity() {
        val currentSession = Any()
        val otherSession = Any()

        assertTrue(
            RawSessionOwnership.Owned.ownsRawSession(
                actualSession = currentSession,
                candidateSession = currentSession,
            ),
        )
        assertFalse(
            RawSessionOwnership.Owned.ownsRawSession(
                actualSession = currentSession,
                candidateSession = otherSession,
            ),
        )
        assertFalse(
            RawSessionOwnership.Relinquished.ownsRawSession(
                actualSession = currentSession,
                candidateSession = currentSession,
            ),
        )
    }

    @Test
    fun exactOwnerIdentityCanRelinquishOnce() {
        val session = Any()

        val relinquished = rawSessionOwnershipAfterRelinquish(
            current = RawSessionOwnership.Owned,
            actualSession = session,
            expectedSession = session,
        )

        assertEquals(RawSessionOwnership.Relinquished, relinquished)
        assertFailsWith<IllegalStateException> {
            rawSessionOwnershipAfterRelinquish(
                current = relinquished,
                actualSession = session,
                expectedSession = session,
            )
        }
    }

    @Test
    fun differentSessionIdentityIsRejectedBeforeStateChanges() {
        val currentSession = Any()
        val replacementSession = Any()

        assertFailsWith<IllegalStateException> {
            rawSessionOwnershipAfterRelinquish(
                current = RawSessionOwnership.Owned,
                actualSession = replacementSession,
                expectedSession = currentSession,
            )
        }
    }
}
