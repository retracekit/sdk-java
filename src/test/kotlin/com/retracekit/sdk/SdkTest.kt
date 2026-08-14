package com.retracekit.sdk

import com.retracekit.sdk.internal.Json
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DedupTest {
	@Test
	fun `extractLastFrameName reads last at frame`() {
		val stack =
			"""
			java.lang.RuntimeException: boom
				at com.example.Foo.bar(Foo.java:10)
				at com.example.App.main(App.java:3)
			""".trimIndent()

		assertEquals("com.example.App.main", extractLastFrameName(stack))
	}

	@Test
	fun `extractLastFrameName strips jpms module`() {
		val stack = "at java.base/java.lang.Thread.run(Thread.java:840)"
		assertEquals("java.lang.Thread.run", extractLastFrameName(stack))
	}

	@Test
	fun `dedup cache suppresses within window`() {
		val cache = DedupCache()
		val key = computeDedupKey("N", "m", "at a.b(C.java:1)")
		assertTrue(cache.tryAcquire(key, 1_000L))
		assertFalse(cache.tryAcquire(key, 1_000L + 1_000L))
		assertTrue(cache.tryAcquire(key, 1_000L + DEDUP_WINDOW_MS))
	}

	@Test
	fun `dedup tryAcquire is atomic under concurrent threads`() {
		val cache = DedupCache()
		val key = computeDedupKey("N", "m", "at a.b(C.java:1)")
		val allowed = AtomicInteger(0)
		val threads = 32
		val latch = CountDownLatch(1)
		val done = CountDownLatch(threads)
		val pool = Executors.newFixedThreadPool(threads)
		try {
			repeat(threads) {
				pool.execute {
					latch.await()
					if (cache.tryAcquire(key, 5_000L)) {
						allowed.incrementAndGet()
					}
					done.countDown()
				}
			}
			latch.countDown()
			assertTrue(done.await(5, TimeUnit.SECONDS))
			assertEquals(1, allowed.get())
		} finally {
			pool.shutdownNow()
		}
	}

	@Test
	fun `dedup overflow evicts oldest instead of clearing`() {
		val cache = DedupCache()
		val now = 10_000L
		repeat(DEDUP_MAX_ENTRIES) { i ->
			assertTrue(cache.tryAcquire("old-$i", now - DEDUP_MAX_ENTRIES + i))
		}
		assertEquals(DEDUP_MAX_ENTRIES, cache.sizeForTesting())
		assertTrue(cache.tryAcquire("newest", now + 1))
		assertTrue(cache.sizeForTesting() <= DEDUP_MAX_ENTRIES)
		// Oldest unique key should have been evicted; a mid-window key should still suppress.
		assertFalse(cache.tryAcquire("old-${DEDUP_MAX_ENTRIES - 1}", now + 1))
		assertTrue(cache.tryAcquire("old-0", now + 1))
	}

	@Test
	fun `dedup release allows immediate reacquire`() {
		val cache = DedupCache()
		val key = computeDedupKey("N", "m", "at a.b(C.java:1)")
		assertTrue(cache.tryAcquire(key, 1_000L))
		assertFalse(cache.tryAcquire(key, 1_001L))
		cache.release(key)
		assertTrue(cache.tryAcquire(key, 1_002L))
	}
}

class JsonTest {
	@Test
	fun `encodes nested objects and escapes`() {
		val json =
			Json.obj(
				"message" to "a\"b\nc",
				"count" to 2,
				"tags" to mapOf("runtime" to "java"),
				"skip" to null,
			)
		assertTrue(json.contains("\"message\":\"a\\\"b\\nc\""))
		assertTrue(json.contains("\"count\":2"))
		assertTrue(json.contains("\"runtime\":\"java\""))
		assertFalse(json.contains("skip"))
	}

	@Test
	fun `raw fragments are not requoted`() {
		val json =
			Json.obj(
				"user" to com.retracekit.sdk.internal.JsonRaw("""{"id":"u1"}"""),
				"items" to listOf(com.retracekit.sdk.internal.JsonRaw("""{"a":1}""")),
			)
		assertEquals("""{"user":{"id":"u1"},"items":[{"a":1}]}""", json)
	}
}

class TransportTest {
	@Test
	fun `deriveSessionsEndpoint replaces error-events`() {
		assertEquals(
			"https://api.retracekit.cloud/api/sessions",
			Transport.deriveSessionsEndpoint("https://api.retracekit.cloud/api/error-events"),
		)
	}
}

class RetraceKitInitTest {
	@AfterEach
	fun tearDown() {
		SdkState.resetForTesting()
	}

	@Test
	fun `init disables when api key blank`() {
		RetraceKit.init(Config(apiKey = "  "))
		assertFalse(SdkState.isCaptureEnabled())
	}

	@Test
	fun `init enables capture and sets runtime tag`() {
		RetraceKit.init(
			Config(
				apiKey = "test-key",
				environment = "test",
				release = "0.1.0",
				enabled = true,
			),
		)
		assertTrue(SdkState.isCaptureEnabled())
		assertEquals("java", Tags.snapshot()["runtime"])
		assertEquals("0.1.1", RetraceKit.getVersion())
	}

	@Test
	fun `concurrent init yields stable sessionId`() {
		val threads = 16
		val latch = CountDownLatch(1)
		val done = CountDownLatch(threads)
		val sessionIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
		val pool = Executors.newFixedThreadPool(threads)
		try {
			repeat(threads) {
				pool.execute {
					latch.await()
					RetraceKit.init(Config(apiKey = "test-key", enabled = false))
					SdkState.sessionId()?.let { sessionIds.add(it) }
					done.countDown()
				}
			}
			latch.countDown()
			assertTrue(done.await(10, TimeUnit.SECONDS))
			assertEquals(1, sessionIds.size)
			assertNotNull(sessionIds.first())
			assertEquals(sessionIds.first(), SdkState.sessionId())
		} finally {
			pool.shutdownNow()
		}
	}

	@Test
	fun `captureException does not throw when disabled`() {
		RetraceKit.init(Config(apiKey = ""))
		RetraceKit.captureException(RuntimeException("no-op"))
	}

	@Test
	fun `user agent format is Java major minor security`() {
		val ua = defaultUserAgent()
		assertTrue(ua.matches(Regex("""^Java \d+(\.\d+){0,2}$""")), ua)
	}

	@Test
	fun `breadcrumbs and tags round trip`() {
		RetraceKit.init(Config(apiKey = "k"))
		RetraceKit.addBreadcrumb(BreadcrumbType.COMMON, "step", "ready")
		RetraceKit.setTag("env", "ci")
		RetraceKit.setUser("user-1")

		assertEquals(1, Breadcrumbs.snapshot().size)
		assertEquals("ci", Tags.snapshot()["env"])
		assertEquals("user-1", UserContext.get()?.id)
	}

	@Test
	fun `breadcrumbs stay bounded under concurrent adds`() {
		RetraceKit.init(Config(apiKey = "k", enabled = false))
		val threads = 16
		val perThread = 50
		val latch = CountDownLatch(1)
		val done = CountDownLatch(threads)
		val pool = Executors.newFixedThreadPool(threads)
		try {
			repeat(threads) { t ->
				pool.execute {
					latch.await()
					repeat(perThread) { i ->
						RetraceKit.addBreadcrumb(BreadcrumbType.COMMON, "t$t", "v$i")
					}
					done.countDown()
				}
			}
			latch.countDown()
			assertTrue(done.await(10, TimeUnit.SECONDS))
			assertEquals(24, Breadcrumbs.snapshot().size)
		} finally {
			pool.shutdownNow()
		}
	}

	@Test
	fun `captureException reentrancy does not loop`() {
		RetraceKit.init(
			Config(
				apiKey = "k",
				endpoint = "http://127.0.0.1:9/api/error-events",
				enabled = true,
			),
		)
		val depth = AtomicInteger(0)
		val maxDepth = AtomicInteger(0)
		class RecursiveError : RuntimeException() {
			override val message: String
				get() {
					val d = depth.incrementAndGet()
					maxDepth.updateAndGet { maxOf(it, d) }
					try {
						RetraceKit.captureException(RuntimeException("inner"))
					} finally {
						depth.decrementAndGet()
					}
					return "outer"
				}
		}
		RetraceKit.captureException(RecursiveError())
		assertEquals(1, maxDepth.get())
	}

	@Test
	fun `handler install calls previous handler`() {
		var previousCalled = false
		val previous = Thread.UncaughtExceptionHandler { _, _ -> previousCalled = true }
		Thread.setDefaultUncaughtExceptionHandler(previous)
		RetraceKit.init(
			Config(
				apiKey = "k",
				endpoint = "http://127.0.0.1:9/api/error-events",
				enabled = true,
			),
		)
		val installed = Handlers.installedHandlerForTesting()
		assertNotNull(installed)
		installed!!.uncaughtException(Thread.currentThread(), RuntimeException("uncaught-test"))
		assertTrue(previousCalled)
	}

	@Test
	fun `payload and headers are sent for error events`() {
		val bodyRef = AtomicReference<String>()
		val authRef = AtomicReference<String>()
		val versionRef = AtomicReference<String>()
		val internalRef = AtomicReference<String>()
		val contentTypeRef = AtomicReference<String>()
		val received = CountDownLatch(1)

		val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.createContext("/api/error-events") { exchange ->
			authRef.set(exchange.requestHeaders.getFirst("Authorization"))
			versionRef.set(exchange.requestHeaders.getFirst("X-RT-SDK-Version"))
			internalRef.set(exchange.requestHeaders.getFirst("X-RT-SDK-Internal"))
			contentTypeRef.set(exchange.requestHeaders.getFirst("Content-Type"))
			bodyRef.set(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))
			exchange.sendResponseHeaders(204, -1)
			exchange.close()
			received.countDown()
		}
		server.createContext("/api/sessions") { exchange ->
			exchange.sendResponseHeaders(204, -1)
			exchange.close()
		}
		server.start()
		try {
			val port = server.address.port
			RetraceKit.init(
				Config(
					apiKey = "test-api-key",
					endpoint = "http://127.0.0.1:$port/api/error-events",
					enabled = true,
				),
			)
			RetraceKit.captureException(RuntimeException("payload-boom"))
			assertTrue(received.await(5, TimeUnit.SECONDS), "timed out waiting for HTTP request")

			assertEquals("Bearer test-api-key", authRef.get())
			assertEquals(VERSION, versionRef.get())
			assertEquals("1", internalRef.get())
			assertTrue(contentTypeRef.get().startsWith("application/json"), contentTypeRef.get())

			val body = bodyRef.get()
			assertNotNull(body)
			assertTrue(body.contains("\"message\":\"payload-boom\""), body)
			assertTrue(body.contains("\"stacktrace\":"), body)
			assertTrue(body.contains("\"userAgent\":\"Java "), body)
		} finally {
			server.stop(0)
		}
	}
}
