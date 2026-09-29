package org.hypixelskyblockmods.storagelens.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import org.slf4j.LoggerFactory

/** Takes snapshots on the caller thread and coalesces pending writes on a daemon worker. */
internal class BackgroundSave {
    private val lock = Any()
    private var dirty = false
    private var pending: (() -> Boolean)? = null
    private var running = false
    private var completion = CompletableFuture.completedFuture<Void>(null)

    fun markDirty() = synchronized(lock) { dirty = true }

    val needsSave: Boolean
        get() = synchronized(lock) { dirty }

    fun submit(snapshot: () -> (() -> Boolean)) = synchronized(lock) {
        if (!dirty) return@synchronized
        pending = snapshot()
        dirty = false
        if (!running) {
            running = true
            completion = CompletableFuture.runAsync(::drain, executor)
        }
    }

    /** Used only at shutdown, profile transitions and explicit cache clearing. */
    fun awaitIdle() {
        synchronized(lock) { completion }.join()
    }

    fun reset() {
        awaitIdle()
        synchronized(lock) { dirty = false }
    }

    private fun drain() {
        while (true) {
            val operation = synchronized(lock) {
                val next = pending
                if (next == null) {
                    running = false
                    return
                }
                pending = null
                next
            }
            val success = runCatching(operation).getOrElse {
                logger.warn("Could not save observed container data", it)
                false
            }
            if (!success) synchronized(lock) {
                // A newer pending snapshot supersedes this failed write.
                if (pending == null) dirty = true
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger("StorageLens Background Save")
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "StorageLens cache writer").apply { isDaemon = true }
        }
    }
}
