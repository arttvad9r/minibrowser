package com.artt.minibrowser.engine

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Files
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class TabManagerRecreationHandoffTest {
    @Test
    fun registryIsStoreScopedAndOneShot() {
        val firstStore = Files.createTempDirectory("recreation-first").toFile()
        val secondStore = Files.createTempDirectory("recreation-second").toFile()
        val handoff = TabManagerRecreationHandoff(
            tabs = listOf(Tab(GeckoSession(), id = 41L, isPrivate = true)),
            selectedId = 41L,
            sequence = 57L,
        )

        try {
            TabManagerRecreationHandoffRegistry.publish(firstStore, handoff)

            assertNull(TabManagerRecreationHandoffRegistry.consume(secondStore))
            assertSame(handoff, TabManagerRecreationHandoffRegistry.consume(firstStore))
            assertNull(TabManagerRecreationHandoffRegistry.consume(firstStore))
        } finally {
            TabManagerRecreationHandoffRegistry.clearForTest(firstStore)
            TabManagerRecreationHandoffRegistry.clearForTest(secondStore)
            firstStore.deleteRecursively()
            secondStore.deleteRecursively()
        }
    }

    @Test
    fun duplicateUnconsumedHandoffFailsClosedInsteadOfReplacingOwnership() {
        val store = Files.createTempDirectory("recreation-duplicate").toFile()
        val first = TabManagerRecreationHandoff(
            tabs = listOf(Tab(GeckoSession(), id = 1L, isPrivate = false)),
            selectedId = 1L,
            sequence = 1L,
        )
        val second = TabManagerRecreationHandoff(
            tabs = listOf(Tab(GeckoSession(), id = 2L, isPrivate = false)),
            selectedId = 2L,
            sequence = 2L,
        )

        try {
            TabManagerRecreationHandoffRegistry.publish(store, first)
            assertFailsWith<IllegalStateException> {
                TabManagerRecreationHandoffRegistry.publish(store, second)
            }
            assertSame(first, TabManagerRecreationHandoffRegistry.consume(store))
        } finally {
            TabManagerRecreationHandoffRegistry.clearForTest(store)
            store.deleteRecursively()
        }
    }
}
