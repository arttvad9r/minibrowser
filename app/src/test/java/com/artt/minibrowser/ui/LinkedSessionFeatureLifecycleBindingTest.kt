package com.artt.minibrowser.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import mozilla.components.support.base.feature.LifecycleAwareFeature

class LinkedSessionFeatureLifecycleBindingTest {
    @Test
    fun lifecycleOwnsNormalStartAndStop() {
        val owner = TestLifecycleOwner()
        val feature = RecordingFeature()
        val binding = LinkedSessionFeatureLifecycleBinding(feature)

        owner.registry.currentState = Lifecycle.State.CREATED
        binding.attach(owner)
        assertEquals(0, feature.starts)
        assertEquals(0, feature.stops)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(1, feature.starts)
        assertEquals(0, feature.stops)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertEquals(1, feature.starts)
        assertEquals(1, feature.stops)
    }

    @Test
    fun detachWhileStartedExplicitlyStopsFeature() {
        val owner = TestLifecycleOwner()
        val feature = RecordingFeature()
        val binding = LinkedSessionFeatureLifecycleBinding(feature)

        owner.registry.currentState = Lifecycle.State.STARTED
        binding.attach(owner)
        assertEquals(1, feature.starts)

        binding.detach()
        assertEquals(1, feature.stops)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertEquals(1, feature.stops)
    }

    @Test
    fun attachingSameOwnerTwiceDoesNotRestartFeature() {
        val owner = TestLifecycleOwner()
        val feature = RecordingFeature()
        val binding = LinkedSessionFeatureLifecycleBinding(feature)

        owner.registry.currentState = Lifecycle.State.STARTED
        binding.attach(owner)
        binding.attach(owner)

        assertEquals(1, feature.starts)
        assertEquals(0, feature.stops)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        // This synthetic owner is mutated only by the calling test thread. Lifecycle 2.11 enforces
        // main-thread access by default; createUnsafe is the documented opt-out for such owners.
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle = registry
    }

    private class RecordingFeature : LifecycleAwareFeature {
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }
}
