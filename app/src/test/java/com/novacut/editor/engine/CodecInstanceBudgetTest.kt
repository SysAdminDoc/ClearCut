package com.novacut.editor.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CodecInstanceBudgetTest {

    @Test
    fun poolQueuesWorkAboveDeclaredCeilingWithoutExceedingIt() = runBlocking {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val pool = CodecLeasePool(
            kind = "test-decoder",
            ceilingForKey = { CodecCeiling(declared = 2, effective = 2) },
            createResource = {
                val now = active.incrementAndGet()
                maximum.updateAndGet { old -> maxOf(old, now) }
                Any()
            },
            closeResource = { active.decrementAndGet() },
        )

        val jobs = (0 until 8).map {
            async(Dispatchers.Default) {
                pool.acquire("audio/mp4").use {
                    delay(25)
                }
            }
        }
        jobs.awaitAll()

        assertEquals("The pool must never exceed the declared ceiling", 2, maximum.get())
        assertEquals(0, active.get())
        val snapshot = pool.snapshots().single()
        assertEquals(2, snapshot.declaredCeiling)
        assertEquals(2, snapshot.effectiveCeiling)
        assertEquals(0, snapshot.active)
        assertEquals(0, snapshot.queued)
    }

    @Test
    fun cancelledWaiterDoesNotConsumeAPermit() = runBlocking {
        val pool = CodecLeasePool(
            kind = "test-retriever",
            ceilingForKey = { CodecCeiling(declared = 1, effective = 1) },
            createResource = { Any() },
            closeResource = {},
        )
        val first = pool.acquire("video/avc")
        val waiter = async(Dispatchers.Default) {
            pool.acquire("video/avc").use { }
        }

        delay(50)
        assertTrue(pool.snapshots().single().queued >= 1)
        waiter.cancel()
        runCatching { waiter.await() }
        assertEquals(0, pool.snapshots().single().queued)

        first.close()
        assertEquals(0, pool.snapshots().single().active)
    }

    @Test
    fun decoderAndRetrieverPoolsShareOneMimeCeiling() = runBlocking {
        val registry = CodecPermitRegistry {
            CodecCeiling(declared = 1, effective = 1)
        }
        val active = AtomicInteger(0)
        val decoderPool = CodecLeasePool(
            kind = "decoder",
            ceilingForKey = { CodecCeiling(1, 1) },
            createResource = { active.incrementAndGet(); Any() },
            closeResource = { active.decrementAndGet() },
            permits = registry,
        )
        val retrieverPool = CodecLeasePool(
            kind = "retriever",
            ceilingForKey = { CodecCeiling(1, 1) },
            createResource = { active.incrementAndGet(); Any() },
            closeResource = { active.decrementAndGet() },
            permits = registry,
        )

        val first = decoderPool.acquire("video/avc")
        val waiter = async(Dispatchers.Default) {
            retrieverPool.acquire("video/avc").use { delay(10) }
        }
        delay(50)
        assertEquals(1, active.get())
        assertEquals(1, registry.snapshots("retriever").single().effectiveCeiling)
        assertTrue(registry.snapshots("retriever").single().queued >= 1)

        first.close()
        waiter.await()
        assertEquals(0, active.get())
        assertEquals(0, registry.snapshots("decoder").single().totalActive)
    }
}
