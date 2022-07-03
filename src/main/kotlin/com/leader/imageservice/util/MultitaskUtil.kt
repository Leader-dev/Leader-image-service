package com.leader.imageservice.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

private fun awaitAndCheck(latch: CountDownLatch, exception: AtomicReference<RuntimeException?>) {
    try {
        latch.await()
    } catch (e: InterruptedException) {
        e.printStackTrace()
        throw InternalErrorException("Interrupted exception occur.", e)
    }
    exception.get()?.let { throw it }
}

fun forTimesAsync(count: Int, action: (Int) -> Unit) {
    val exception = AtomicReference<RuntimeException?>(null)
    val latch = CountDownLatch(count)
    for (i in 0 until count) {
        Thread {
            try {
                action(i)
            } catch (e: RuntimeException) {
                exception.set(e)
            } finally {
                latch.countDown()
            }
        }.start()
    }
    awaitAndCheck(latch, exception)
}

fun <T> Collection<T>.forEachAsync(action: (T) -> Unit) {
    val exception = AtomicReference<RuntimeException?>(null)
    val latch = CountDownLatch(this.size)
    for (item in this) {
        Thread {
            try {
                action(item)
            } catch (e: RuntimeException) {
                exception.set(e)
            } finally {
                latch.countDown()
            }
        }.start()
    }
    awaitAndCheck(latch, exception)
}