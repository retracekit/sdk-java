package com.retracekit.sdk

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/**
 * Retrace Kit JVM SDK facade.
 *
 * Kotlin and Java entrypoints for init, capture, breadcrumbs, user, and tags.
 */
object RetraceKit {
	@JvmStatic
	fun init(config: Config) {
		SdkState.init(config)
	}

	@JvmStatic
	fun captureException(error: Throwable) {
		Capture.captureException(error)
	}

	@JvmStatic
	fun captureException(error: Throwable, contextOverrides: Map<String, Any?>) {
		Capture.captureException(error, contextOverrides)
	}

	@JvmStatic
	fun addBreadcrumb(type: BreadcrumbType, name: String, value: String) {
		Breadcrumbs.add(type = type, name = name, value = value)
	}

	@JvmStatic
	fun addBreadcrumb(
		type: BreadcrumbType,
		name: String,
		value: String,
		status: Int?,
		duration: Int?,
	) {
		Breadcrumbs.add(type = type, name = name, value = value, status = status, duration = duration)
	}

	@JvmStatic
	fun setUser(id: String) {
		UserContext.set(id)
	}

	@JvmStatic
	fun setUser(user: RetraceKitUser) {
		UserContext.set(user)
	}

	@JvmStatic
	fun setTag(name: String, value: String) {
		Tags.set(name, value)
	}

	@JvmStatic
	fun getVersion(): String = VERSION
}

internal object SdkState {
	private val logger = Logger.getLogger("retrace_kit")

	@Volatile
	private var config: InternalConfig? = null

	private val sessionId = AtomicReference<String?>(null)

	@Volatile
	private var warnedAboutMissingApiKey: Boolean = false

	fun init(publicConfig: Config) {
		try {
			ensureSessionId()
			Tags.set("runtime", "java")

			val normalizedEndpoint =
				publicConfig.endpoint.trim().ifEmpty { Config.DEFAULT_ENDPOINT }
			val normalizedServerUrl =
				publicConfig.serverUrl?.trim()?.takeIf { it.isNotEmpty() }
			val trimmedApiKey = publicConfig.apiKey.trim()

			Handlers.uninstall()

			if (trimmedApiKey.isEmpty()) {
				if (!warnedAboutMissingApiKey) {
					warnedAboutMissingApiKey = true
					logger.warning(
						"[retrace-kit sdk] init requires a non-blank apiKey; event sending will be disabled.",
					)
				}
				config =
					InternalConfig(
						apiKey = "",
						endpoint = normalizedEndpoint,
						release = publicConfig.release,
						environment = publicConfig.environment,
						serverUrl = normalizedServerUrl,
						enabled = false,
					)
				return
			}

			config =
				InternalConfig(
					apiKey = trimmedApiKey,
					endpoint = normalizedEndpoint,
					release = publicConfig.release,
					environment = publicConfig.environment,
					serverUrl = normalizedServerUrl,
					enabled = publicConfig.enabled,
				)

			if (publicConfig.enabled) {
				Handlers.install()
				pingSession()
			}
		} catch (_: Throwable) {
			// Never throw into host application code.
		}
	}

	fun currentConfig(): InternalConfig? = config

	fun isCaptureEnabled(): Boolean {
		val cfg = config
		return cfg != null && cfg.enabled && cfg.apiKey.isNotEmpty()
	}

	fun sessionId(): String? = sessionId.get()

	fun pingSession() {
		try {
			if (!isCaptureEnabled()) {
				return
			}
			val cfg = config ?: return
			val sid = sessionId.get() ?: return
			val body =
				Transport.buildSessionJson(
					sessionId = sid,
					user = UserContext.get(),
					release = cfg.release,
					environment = cfg.environment,
				)
			Transport.sendSessionPing(
				body,
				cfg.apiKey,
				Transport.deriveSessionsEndpoint(cfg.endpoint),
			)
		} catch (_: Throwable) {
			// Never throw into host application code.
		}
	}

	private fun ensureSessionId() {
		if (sessionId.get() == null) {
			sessionId.compareAndSet(null, UUID.randomUUID().toString())
		}
	}

	fun resetForTesting() {
		Handlers.resetForTesting()
		config = null
		sessionId.set(null)
		warnedAboutMissingApiKey = false
		Breadcrumbs.resetForTesting()
		Tags.resetForTesting()
		UserContext.resetForTesting()
		Capture.resetForTesting()
	}
}
