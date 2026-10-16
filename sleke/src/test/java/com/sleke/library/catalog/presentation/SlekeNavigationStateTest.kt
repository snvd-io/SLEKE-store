package com.sleke.library.catalog.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlekeNavigationStateTest {
    @Test fun enterpriseBackAndAccessLossRestoreCatalog() {
        assertFalse(SlekeNavigationState(true).interceptsBack(isActive = false))
        assertTrue(SlekeNavigationState(true).interceptsBack(isActive = true))
        assertTrue(SlekeNavigationState(false).enterEnterprise(true).enterpriseVisible)
        assertFalse(SlekeNavigationState(true).back().enterpriseVisible)
        assertFalse(SlekeNavigationState(true).reconcileAccess(false).enterpriseVisible)
        assertFalse(SlekeNavigationState(false).enterEnterprise(false).enterpriseVisible)
    }
}
