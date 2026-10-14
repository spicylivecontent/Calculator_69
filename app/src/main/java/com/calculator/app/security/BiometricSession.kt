package com.calculator.app.security

/** Single-use gate for callbacks, including callbacks delivered after prompt disposal. */
internal class BiometricSession(
    private val onSuccess: () -> Unit,
    private val onFailure: () -> Unit
) {
    private val active = java.util.concurrent.atomic.AtomicBoolean(true)

    fun succeed() {
        if (active.compareAndSet(true, false)) onSuccess()
    }

    fun fail() {
        if (active.compareAndSet(true, false)) onFailure()
    }

    fun cancel() {
        active.set(false)
    }
}
