package com.sleke.library.runtime

import com.sleke.library.runtime.data.DiagnosticPolicy
import com.sleke.library.runtime.data.DiagnosticPriority
import com.sleke.library.runtime.data.DiagnosticsSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DiagnosticPolicyTest {
    private val breadcrumbs = mutableListOf<Pair<DiagnosticPriority, String>>()
    private val errors = mutableListOf<Throwable>()
    private val policy = DiagnosticPolicy(object : DiagnosticsSink {
        override fun breadcrumb(priority: DiagnosticPriority, message: String) {
            breadcrumbs += priority to message
        }
        override fun record(throwable: Throwable) {
            errors += throwable
        }
    })

    @Test
    fun warningCreatesBreadcrumbWithoutException() {
        policy.log(5, RuntimeException("credential=secret@example.com"))
        assertEquals(listOf(DiagnosticPriority.WARNING to "SLEKE diagnostic warning"), breadcrumbs)
        assertEquals(emptyList<Throwable>(), errors)
    }

    @Test
    fun errorRecordsOriginalThrowableOnceAndUsesFixedContext() {
        val original = IllegalStateException("token=secret accountDocument=email@example.com")
        policy.log(6, original)
        assertEquals(listOf(DiagnosticPriority.ERROR to "SLEKE diagnostic error"), breadcrumbs)
        assertSame(original, errors.single())
    }

    @Test
    fun lowerPrioritiesAreIgnoredAndErrorWithoutThrowableIsBreadcrumbOnly() {
        (2..4).forEach { policy.log(it, IllegalStateException("secret")) }
        policy.log(6, null)
        assertEquals(listOf(DiagnosticPriority.ERROR to "SLEKE diagnostic error"), breadcrumbs)
        assertEquals(emptyList<Throwable>(), errors)
    }
}
