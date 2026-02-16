package com.github.aidarkhusainov.reqrun.uitest

import java.time.Duration

fun waitFor(
    description: String,
    timeout: Duration = Duration.ofSeconds(20),
    interval: Duration = Duration.ofMillis(200),
    condition: () -> Boolean,
) {
    val deadline = System.nanoTime() + timeout.toNanos()
    var lastError: Throwable? = null
    while (System.nanoTime() < deadline) {
        try {
            if (condition()) return
        } catch (t: Throwable) {
            lastError = t
        }
        Thread.sleep(interval.toMillis())
    }
    val message = buildString {
        append("Timed out waiting for: ").append(description)
        if (lastError != null) {
            append(". Last error: ").append(lastError!!.message)
        }
    }
    throw AssertionError(message, lastError)
}
