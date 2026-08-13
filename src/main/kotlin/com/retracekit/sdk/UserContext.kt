package com.retracekit.sdk

internal object UserContext {
	@Volatile
	private var currentUser: RetraceKitUser? = null

	fun sanitize(user: RetraceKitUser?): RetraceKitUser? {
		val id = user?.id?.trim().orEmpty()
		if (id.isEmpty()) {
			return null
		}
		return RetraceKitUser(id)
	}

	fun set(user: RetraceKitUser?) {
		try {
			val normalized = sanitize(user) ?: return
			currentUser = normalized
			if (SdkState.isCaptureEnabled()) {
				SdkState.pingSession()
			}
		} catch (_: Exception) {
			// Never throw into host application code.
		}
	}

	fun set(id: String) {
		set(RetraceKitUser(id))
	}

	fun get(): RetraceKitUser? = currentUser

	fun resetForTesting() {
		currentUser = null
	}
}
