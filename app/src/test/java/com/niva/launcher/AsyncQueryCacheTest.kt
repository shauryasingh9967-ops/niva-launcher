package com.niva.launcher

import com.niva.launcher.data.AsyncQueryCache
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsyncQueryCacheTest {
    @Test
    fun cachesEmptyReadyResultsUntilTheirExpiry() = runBlocking {
        val clock = AtomicLong(100)
        val calls = AtomicInteger()
        AsyncQueryCache<String, List<String>>(
            ttlMillis = 30,
            shouldCache = { true },
            load = { calls.incrementAndGet(); emptyList() },
            clock = clock::get,
        ).use { cache ->
            assertEquals(emptyList<String>(), cache.get("app"))
            assertEquals(emptyList<String>(), cache.peek("app"))
            cache.get("app")
            assertEquals(1, calls.get())
            clock.set(130)
            assertNull(cache.peek("app"))
            cache.get("app")
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun touchPrefetchAndPopupShareOneInFlightQuery() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            AsyncQueryCache<String, String>(30_000, { true }, {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                "ready"
            }).use { cache ->
                val touch = async(start = CoroutineStart.UNDISPATCHED) { cache.get("app") }
                started.await()
                val popup = async(start = CoroutineStart.UNDISPATCHED) { cache.get("app") }
                assertEquals(1, calls.get())
                release.complete(Unit)
                assertEquals(listOf("ready", "ready"), awaitAll(touch, popup))
            }
        }
    }

    @Test
    fun cancellingGestureDoesNotCancelTheSharedQuery() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            AsyncQueryCache<String, String>(30_000, { true }, {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                "ready"
            }).use { cache ->
                val gesture = async { cache.get("app") }
                started.await()
                gesture.cancelAndJoin()
                val popup = async(start = CoroutineStart.UNDISPATCHED) { cache.get("app") }
                release.complete(Unit)
                assertEquals("ready", popup.await())
                assertEquals(1, calls.get())
            }
        }
    }

    @Test
    fun invalidatedInFlightResultCannotReplaceANewerValue() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val releaseOldQuery = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            AsyncQueryCache<String, Int>(30_000, { true }, {
                val version = calls.incrementAndGet()
                if (version == 1) {
                    started.complete(Unit)
                    releaseOldQuery.await()
                }
                version
            }).use { cache ->
                val old = async { cache.get("app") }
                started.await()
                cache.invalidate { it == "app" }
                assertEquals(2, cache.get("app"))
                releaseOldQuery.complete(Unit)
                old.await()
                assertEquals(2, cache.peek("app"))
            }
        }
    }

    @Test
    fun errorsAndPermissionFailuresAreNotCached() = runBlocking {
        val calls = AtomicInteger()
        AsyncQueryCache<String, String>(30_000, { it == "ready" }, {
            if (calls.incrementAndGet() == 1) "error" else "ready"
        }).use { cache ->
            assertEquals("error", cache.get("app"))
            assertNull(cache.peek("app"))
            assertEquals("ready", cache.get("app"))
        }
    }

    @Test
    fun prefetchDoesNotIssueMoreThanThreeParallelQueries() = runBlocking {
        withTimeout(5_000) {
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val atLimit = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            AsyncQueryCache<Int, Int>(30_000, { true }, { key ->
                val count = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, count) }
                if (count == 3) atLimit.complete(Unit)
                release.await()
                active.decrementAndGet()
                key
            }).use { cache ->
                val requests = (1..12).map { async { cache.get(it) } }
                atLimit.await()
                release.complete(Unit)
                requests.awaitAll()
                assertEquals(3, peak.get())
            }
        }
    }
}
