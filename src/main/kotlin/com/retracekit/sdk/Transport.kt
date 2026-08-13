package com.retracekit.sdk

import com.retracekit.sdk.internal.Json
import com.retracekit.sdk.internal.JsonRaw
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger

internal object Transport {
	private val logger = Logger.getLogger("retrace_kit")
	private const val SDK_INTERNAL_HEADER = "X-RT-SDK-Internal"
	private const val UNCAUGHT_REQUEST_TIMEOUT_SECONDS = 2L
	private const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 10L
	private val threadCounter = AtomicInteger(0)

	private val httpClient: HttpClient =
		HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build()

	private val executor: ExecutorService =
		ThreadPoolExecutor(
			2,
			4,
			60L,
			TimeUnit.SECONDS,
			ArrayBlockingQueue(64),
			{ runnable ->
				Thread(runnable, "retrace-kit-transport-${threadCounter.incrementAndGet()}").apply {
					isDaemon = true
				}
			},
			ThreadPoolExecutor.AbortPolicy(),
		)

	fun deriveSessionsEndpoint(errorEventsEndpoint: String): String {
		if (errorEventsEndpoint.contains("/error-events")) {
			return errorEventsEndpoint.replace("/error-events", "/sessions")
		}
		val trimmed = errorEventsEndpoint.trimEnd('/')
		val lastSlash = trimmed.lastIndexOf('/')
		return if (lastSlash >= 0) {
			"${trimmed.substring(0, lastSlash)}/sessions"
		} else {
			"$trimmed/sessions"
		}
	}

	/** @return true if the send was accepted onto the transport queue. */
	fun sendErrorEvent(payloadJson: String, apiKey: String, endpoint: String): Boolean =
		sendInBackground(endpoint, apiKey, payloadJson, "error event", DEFAULT_REQUEST_TIMEOUT_SECONDS)

	/**
	 * Best-effort synchronous send for uncaught-exception flush.
	 * Uses a short request timeout so the host is not blocked indefinitely.
	 *
	 * @return true if the HTTP call completed (any status); false on transport failure.
	 */
	fun sendErrorEventSync(payloadJson: String, apiKey: String, endpoint: String): Boolean =
		postJson(endpoint, apiKey, payloadJson, "error event", UNCAUGHT_REQUEST_TIMEOUT_SECONDS)

	fun sendSessionPing(payloadJson: String, apiKey: String, endpoint: String) {
		sendInBackground(endpoint, apiKey, payloadJson, "session ping", DEFAULT_REQUEST_TIMEOUT_SECONDS)
	}

	private fun sendInBackground(
		url: String,
		apiKey: String,
		body: String,
		eventLabel: String,
		timeoutSeconds: Long,
	): Boolean {
		return try {
			executor.execute {
				postJson(url, apiKey, body, eventLabel, timeoutSeconds)
			}
			true
		} catch (err: RejectedExecutionException) {
			logger.warning("[retrace-kit sdk] transport queue full; dropping $eventLabel")
			false
		} catch (err: Throwable) {
			logger.log(Level.SEVERE, "[retrace-kit sdk] failed to send $eventLabel: $err")
			false
		}
	}

	private fun postJson(
		url: String,
		apiKey: String,
		body: String,
		eventLabel: String,
		timeoutSeconds: Long,
	): Boolean {
		return try {
			val request =
				HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(timeoutSeconds))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer $apiKey")
					.header("X-RT-SDK-Version", VERSION)
					.header(SDK_INTERNAL_HEADER, "1")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build()

			val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
			if (response.statusCode() >= 400) {
				logger.warning(
					"[retrace-kit sdk] failed to send $eventLabel: HTTP ${response.statusCode()}",
				)
			}
			true
		} catch (err: Throwable) {
			logger.log(Level.SEVERE, "[retrace-kit sdk] failed to send $eventLabel: $err")
			false
		}
	}

	fun buildErrorEventJson(
		timestamp: String,
		name: String?,
		message: String,
		stacktrace: String,
		url: String?,
		release: String?,
		environment: String?,
		userAgent: String,
		user: RetraceKitUser?,
		breadcrumbs: List<Breadcrumb>,
		tags: Map<String, String>,
		sessionId: String?,
	): String {
		val breadcrumbJson =
			breadcrumbs.map { crumb ->
				JsonRaw(
					Json.obj(
						"type" to crumb.type.wireValue(),
						"name" to crumb.name,
						"value" to crumb.value,
						"capturedAt" to crumb.capturedAt,
						"status" to crumb.status,
						"duration" to crumb.duration,
					),
				)
			}

		return Json.obj(
			"timestamp" to timestamp,
			"name" to name,
			"message" to message,
			"stacktrace" to stacktrace,
			"url" to url,
			"release" to release,
			"environment" to environment,
			"userAgent" to userAgent,
			"user" to user?.let { JsonRaw(Json.obj("id" to it.id)) },
			"breadcrumbs" to if (breadcrumbs.isEmpty()) null else breadcrumbJson,
			"tags" to if (tags.isEmpty()) null else tags,
			"sessionId" to sessionId,
		)
	}

	fun buildSessionJson(
		sessionId: String,
		user: RetraceKitUser?,
		release: String?,
		environment: String?,
	): String =
		Json.obj(
			"sessionId" to sessionId,
			"user" to user?.let { JsonRaw(Json.obj("id" to it.id)) },
			"release" to release,
			"environment" to environment,
		)
}
