package com.retracekit.sdk

import com.retracekit.sdk.internal.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
		assertTrue(cache.shouldSend(key, 1_000L))
		cache.recordSend(key, 1_000L)
		assertFalse(cache.shouldSend(key, 1_000L + 1_000L))
		assertTrue(cache.shouldSend(key, 1_000L + DEDUP_WINDOW_MS))
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
		assertEquals("0.1.0", RetraceKit.getVersion())
	}

	@Test
	fun `captureException does not throw when disabled`() {
		RetraceKit.init(Config(apiKey = ""))
		RetraceKit.captureException(RuntimeException("no-op"))
	}

	@Test
	fun `user agent format is Java version`() {
		val ua = defaultUserAgent()
		assertTrue(ua.startsWith("Java "), ua)
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
}
