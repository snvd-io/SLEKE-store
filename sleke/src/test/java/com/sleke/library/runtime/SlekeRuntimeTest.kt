package com.sleke.library.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class SlekeRuntimeTest {
    @Test
    fun initializationTwicePlantsOneOwnedTreeAndKeepsOtherTrees() {
        val registry = FakeRegistry()
        val initializer = RuntimeInitializer(registry)
        initializer.initialize(RuntimeConfig(debug = false))
        initializer.initialize(RuntimeConfig(debug = false))
        assertEquals(listOf(false), registry.registrations)
        assertEquals(listOf("unrelated"), registry.otherTrees)
    }

    @Test
    fun debugConfigurationDoesNotRegisterReleaseReporting() {
        val registry = FakeRegistry()
        RuntimeInitializer(registry).initialize(RuntimeConfig(debug = true))
        assertEquals(listOf(true), registry.registrations)
    }

    private class FakeRegistry : LoggingRegistry {
        val registrations = mutableListOf<Boolean>()
        val otherTrees = listOf("unrelated")
        override fun registerOwned(debug: Boolean) {
            registrations += debug
        }
        override fun isOwnedRegistered() = registrations.isNotEmpty()
    }
}
