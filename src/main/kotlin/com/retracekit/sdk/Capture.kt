package com.retracekit.sdk

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

internal fun utcNowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

internal fun defaultUserAgent(): String = "Java ${Runtime.version()}"

internal fun throwableStacktrace(error: Throwable): String {
	val writer = StringWriter()
	error.printStackTrace(PrintWriter(writer))
	return writer.toString().trimEnd()
}

internal object Capture {
	private val dedupCache = DedupCache()

	private val capturing = ThreadLocal.withInitial { false }

	fun captureException(error: Throwable, contextOverrides: Map<String, Any?>? = null) {
		if (capturing.get() == true) {
			return
		}
		capturing.set(true)
		try {
			val cfg = SdkState.currentConfig() ?: return
			if (!cfg.enabled || cfg.apiKey.isEmpty()) {
				return
			}

			val name = error::class.java.name
			val message = error.message?.takeIf { it.isNotBlank() } ?: name
			val stacktrace = throwableStacktrace(error)

			var dedupKey: String? = null
			var allowSend = true
			try {
				dedupKey = computeDedupKey(name, message, stacktrace)
				allowSend = dedupCache.shouldSend(dedupKey, System.currentTimeMillis())
			} catch (_: Exception) {
				// Fail-open when dedup logic throws.
			}

			if (!allowSend) {
				return
			}

			val payload =
				buildPayload(
					config = cfg,
					name = name,
					message = message,
					stacktrace = stacktrace,
					contextOverrides = contextOverrides,
				)
			Transport.sendErrorEvent(payload, cfg.apiKey, cfg.endpoint)

			if (dedupKey != null) {
				try {
					dedupCache.recordSend(dedupKey, System.currentTimeMillis())
				} catch (_: Exception) {
					// Ignore cache write failures.
				}
			}
		} catch (_: Exception) {
			// Never throw into host application code.
		} finally {
			capturing.set(false)
		}
	}

	private fun buildPayload(
		config: InternalConfig,
		name: String,
		message: String,
		stacktrace: String,
		contextOverrides: Map<String, Any?>?,
	): String {
		val user = UserContext.get()
		val breadcrumbs = Breadcrumbs.snapshot()
		val tags = Tags.snapshot()
		val sessionId = SdkState.sessionId()

		var url = config.serverUrl
		var release = config.release
		var environment = config.environment
		var userAgent = defaultUserAgent()
		var resolvedName: String? = name
		var resolvedMessage = message
		var resolvedStacktrace = stacktrace
		var resolvedSessionId = sessionId
		var resolvedTags = tags
		val resolvedBreadcrumbs = breadcrumbs

		if (contextOverrides != null) {
			contextOverrides["url"]?.let { url = it.toString() }
			contextOverrides["release"]?.let { release = it.toString() }
			contextOverrides["environment"]?.let { environment = it.toString() }
			contextOverrides["userAgent"]?.let { userAgent = it.toString() }
			contextOverrides["name"]?.let { resolvedName = it.toString() }
			contextOverrides["message"]?.let { resolvedMessage = it.toString() }
			contextOverrides["stacktrace"]?.let { resolvedStacktrace = it.toString() }
			contextOverrides["sessionId"]?.let { resolvedSessionId = it.toString() }
			@Suppress("UNCHECKED_CAST")
			(contextOverrides["tags"] as? Map<String, String>)?.let { resolvedTags = it }
		}

		if (resolvedMessage.isBlank()) {
			resolvedMessage = "Unknown error"
		}

		return Transport.buildErrorEventJson(
			timestamp = utcNowIso(),
			name = resolvedName,
			message = resolvedMessage,
			stacktrace = resolvedStacktrace,
			url = url,
			release = release,
			environment = environment,
			userAgent = userAgent,
			user = user,
			breadcrumbs = resolvedBreadcrumbs,
			tags = resolvedTags,
			sessionId = resolvedSessionId,
		)
	}

	fun resetForTesting() {
		capturing.set(false)
		dedupCache.clear()
	}
}
