package com.retracekit.sdk

import java.util.concurrent.ConcurrentHashMap

internal object Tags {
	private val tags = ConcurrentHashMap<String, String>()

	fun set(name: String, value: String) {
		try {
			val key = name.trim()
			if (key.isEmpty()) {
				return
			}
			tags[key] = value
		} catch (_: Exception) {
			// Never throw into host application code.
		}
	}

	fun snapshot(): Map<String, String> = HashMap(tags)

	fun resetForTesting() {
		tags.clear()
	}
}
