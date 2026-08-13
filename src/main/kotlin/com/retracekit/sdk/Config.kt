package com.retracekit.sdk

/**
 * Public SDK configuration.
 *
 * From Java use [Builder].
 */
data class Config(
	val apiKey: String,
	val endpoint: String = DEFAULT_ENDPOINT,
	val release: String? = null,
	val environment: String? = null,
	val serverUrl: String? = null,
	val enabled: Boolean = true,
) {
	class Builder {
		private var apiKey: String = ""
		private var endpoint: String = DEFAULT_ENDPOINT
		private var release: String? = null
		private var environment: String? = null
		private var serverUrl: String? = null
		private var enabled: Boolean = true

		fun apiKey(value: String) = apply { apiKey = value }

		fun endpoint(value: String) = apply { endpoint = value }

		fun release(value: String?) = apply { release = value }

		fun environment(value: String?) = apply { environment = value }

		fun serverUrl(value: String?) = apply { serverUrl = value }

		fun enabled(value: Boolean) = apply { enabled = value }

		fun build(): Config =
			Config(
				apiKey = apiKey,
				endpoint = endpoint,
				release = release,
				environment = environment,
				serverUrl = serverUrl,
				enabled = enabled,
			)
	}

	companion object {
		const val DEFAULT_ENDPOINT: String = "https://api.retracekit.cloud/api/error-events"

		@JvmStatic
		fun builder(): Builder = Builder()
	}
}

internal data class InternalConfig(
	val apiKey: String,
	val endpoint: String,
	val release: String?,
	val environment: String?,
	val serverUrl: String?,
	val enabled: Boolean,
)

data class RetraceKitUser(
	val id: String,
)

enum class BreadcrumbType {
	REQUEST,
	ROUTE,
	COMMON,
	;

	fun wireValue(): String =
		when (this) {
			REQUEST -> "request"
			ROUTE -> "route"
			COMMON -> "common"
		}
}

data class Breadcrumb(
	val type: BreadcrumbType,
	val name: String,
	val value: String,
	val capturedAt: String,
	val status: Int? = null,
	val duration: Int? = null,
)
