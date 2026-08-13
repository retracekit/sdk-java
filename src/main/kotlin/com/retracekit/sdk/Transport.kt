package com.retracekit.sdk

import com.retracekit.sdk.internal.Json
import com.retracekit.sdk.internal.JsonRaw
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

internal object Transport {
	private val logger = Logger.getLogger("retrace_kit")
	private const val SDK_INTERNAL_HEADER = "X-RT-SDK-Internal"

	private val httpClient: HttpClient =
		HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build()

	private val executor: ExecutorService =
		Executors.newCachedThreadPool { runnable ->
			Thread(runnable, "retrace-kit-transport").apply {
				isDaemon = true
			}
		}

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

	fun sendErrorEvent(payloadJson: String, apiKey: String, endpoint: String) {
		sendInBackground(endpoint, apiKey, payloadJson, "error event")
	}

	fun sendSessionPing(payloadJson: String, apiKey: String, endpoint: String) {
		sendInBackground(endpoint, apiKey, payloadJson, "session ping")
	}

	private fun sendInBackground(url: String, apiKey: String, body: String, eventLabel: String) {
		try {
			executor.execute {
				postJson(url, apiKey, body, eventLabel)
			}
		} catch (err: Exception) {
			logger.log(Level.SEVERE, "[retrace-kit sdk] failed to send $eventLabel: $err")
		}
	}

	private fun postJson(url: String, apiKey: String, body: String, eventLabel: String) {
		try {
			val request =
				HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(10))
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
		} catch (err: Exception) {
			logger.log(Level.SEVERE, "[retrace-kit sdk] failed to send $eventLabel: $err")
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
