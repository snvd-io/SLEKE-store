package com.sleke.integration.runtime

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aurora.store.data.activity.MicroGInstallerActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoenixProcessTest {
    @Test
    fun phoenixProcessDeliversRestartIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // This activity finishes immediately without files and never starts an installation.
        val destination = Intent(context, MicroGInstallerActivity::class.java)
        val monitor = instrumentation.addMonitor(
            MicroGInstallerActivity::class.java.name,
            null,
            false
        )
        try {
            context.startActivity(
                Intent().apply {
                    setClassName(
                        context.packageName,
                        "com.jakewharton.processphoenix.PhoenixActivity"
                    )
                    // Clear a restored host activity so the Hilt test application cannot resume it.
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putParcelableArrayListExtra("phoenix_restart_intents", arrayListOf(destination))
                    // Exercise the real secondary process without killing the test runner's PID.
                    putExtra("phoenix_main_process_pid", Int.MAX_VALUE)
                }
            )
            assertNotNull(
                "Phoenix must deliver the restart intent instead of crashing during application injection",
                monitor.waitForActivityWithTimeout(15_000)
            )
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
