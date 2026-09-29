package org.hypixelskyblockmods.storagelens.util

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BackgroundSaveTest {
    @Test
    fun `unchanged saves skip snapshot creation and serialization`() {
        val save = BackgroundSave()
        repeat(100) { save.submit { error("Unchanged container must not be serialized") } }
        var writes = 0
        save.markDirty()
        save.submit { { writes++; true } }
        save.awaitIdle()
        repeat(100) { save.submit { error("Already saved container must not be serialized") } }
        assertEquals(1, writes)
    }

    @Test
    fun `capture runs on caller and a blocked write does not block submitting`() {
        val save = BackgroundSave()
        val caller = Thread.currentThread()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var snapshotThread: Thread? = null
        var writerThread: Thread? = null
        var liveValue = "old profile contents"
        var written: String? = null
        save.markDirty()
        save.submit {
            snapshotThread = Thread.currentThread()
            val snapshot = liveValue
            val write: () -> Boolean = {
                writerThread = Thread.currentThread()
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                written = snapshot
                true
            }
            write
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            liveValue = "new profile contents"
            assertEquals(caller, snapshotThread)
            assertNotEquals(caller, writerThread)
        } finally {
            release.countDown()
            save.awaitIdle()
        }
        assertEquals("old profile contents", written)
    }

    @Test
    fun `pending snapshots coalesce and shutdown waits for newest contents`() {
        val save = BackgroundSave()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = mutableListOf<Int>()
        save.markDirty()
        save.submit { {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            writes.add(0)
            true
        } }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            for (value in 1..100) {
                save.markDirty()
                save.submit { { writes.add(value); true } }
            }
        } finally {
            release.countDown()
            save.awaitIdle()
        }
        assertEquals(listOf(0, 100), writes)
        assertFalse(save.needsSave)
    }

    @Test
    fun `failed writes remain dirty and can be retried`() {
        val save = BackgroundSave()
        save.markDirty()
        save.submit { { false } }
        save.awaitIdle()
        assertTrue(save.needsSave)
        var writes = 0
        save.submit { { writes++; true } }
        save.awaitIdle()
        assertEquals(1, writes)
        assertFalse(save.needsSave)
    }

    @Test
    fun `changes arriving during a write stay dirty`() {
        val save = BackgroundSave()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        save.markDirty()
        save.submit { {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            true
        } }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            save.markDirty()
        } finally {
            release.countDown()
            save.awaitIdle()
        }
        assertTrue(save.needsSave)
    }

    @Test
    fun `reset drains previous profile writes before clear or reload`(@TempDir root: Path) {
        val save = BackgroundSave()
        val file = root.resolve("old-profile.json")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        save.markDirty()
        save.submit { {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Files.writeString(file, "old contents")
            true
        } }
        val reset = CompletableFuture.runAsync { save.reset() }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { reset.get(100, TimeUnit.MILLISECONDS) }
        } finally {
            release.countDown()
            reset.get(5, TimeUnit.SECONDS)
        }
        assertEquals("old contents", Files.readString(file))
        Files.delete(file)
        save.submit { error("Reset cache must not recreate deleted observations") }
        save.awaitIdle()
        assertFalse(Files.exists(file))
    }
}
