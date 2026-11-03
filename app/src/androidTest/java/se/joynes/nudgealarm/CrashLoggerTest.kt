package se.joynes.nudgealarm

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.storage.EventLogStore
import se.joynes.nudgealarm.core.event.Event

class CrashLoggerTest {
    @Test fun crashesAreRecordedWithBoundedTraceBeforeTheExistingHandlerReceivesThem() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("crash_logger_test_$name", mode)
        }
        val log = EventLogStore(context)
        log.clear()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val delivered = mutableListOf<Throwable>()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, error -> delivered.add(error) }
            installCrashLogger(context)
            val error = IllegalStateException("Diagnostic fixture").apply {
                stackTrace = Array(200) { StackTraceElement("Example", "method", "Example.kt", it) }
            }
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), RuntimeException())
            val crashes = log.getAll().filterIsInstance<Event.Crash>()
            assertEquals(2, crashes.size)
            assertEquals("Diagnostic fixture", crashes[0].exceptionMessage)
            assertEquals(IllegalStateException::class.java.name, crashes[0].exceptionClass)
            assertEquals(3000, crashes[0].stackTrace.length)
            assertEquals("(no message)", crashes[1].exceptionMessage)
            assertSame(error, delivered.first())
            assertEquals(2, delivered.size)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            log.clear()
        }
    }
}
