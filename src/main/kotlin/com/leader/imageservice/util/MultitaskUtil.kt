package com.leader.imageservice.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

object MultitaskUtil {
    private fun awaitAndCheck(latch: CountDownLatch, exception: AtomicReference<RuntimeException>) {
        try {
            latch.await()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            throw InternalErrorException("Interrupted exception occur.", e)
        }
        if (exception.get() != null) {
            throw exception.get()!!
        }
    }

    fun forI(count: Int, consumer: Consumer<Int>) {
        val exception = AtomicReference<RuntimeException>()
        val latch = CountDownLatch(count)
        for (i in 0 until count) {
            Thread {
                try {
                    consumer.accept(i)
                } catch (e: RuntimeException) {
                    exception.set(e)
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        awaitAndCheck(latch, exception)
    }

    fun <T> forEach(items: Collection<T>, consumer: Consumer<T>) {
        val exception = AtomicReference<RuntimeException>()
        val latch = CountDownLatch(items.size)
        for (item in items) {
            Thread {
                try {
                    consumer.accept(item)
                } catch (e: RuntimeException) {
                    exception.set(e)
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        awaitAndCheck(latch, exception)
    }
}