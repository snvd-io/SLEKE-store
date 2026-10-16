package com.sleke.library.runtime.data

enum class DiagnosticPriority { WARNING, ERROR }

interface DiagnosticsSink {
    fun breadcrumb(priority: DiagnosticPriority, message: String)
    fun record(throwable: Throwable)
}

/** Avoid forwarding arbitrary log messages, account data or tags to diagnostics. */
class DiagnosticPolicy(private val sink: DiagnosticsSink) {
    fun log(priority: Int, throwable: Throwable?) {
        when (priority) {
            5 -> sink.breadcrumb(DiagnosticPriority.WARNING, "SLEKE diagnostic warning")
            6, 7 -> {
                sink.breadcrumb(DiagnosticPriority.ERROR, "SLEKE diagnostic error")
                if (throwable != null) sink.record(throwable)
            }
        }
    }
}
