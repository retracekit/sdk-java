package com.retracekit.sdk

import java.util.concurrent.ConcurrentHashMap

internal const val DEDUP_WINDOW_MS = 30_000L
private const val DEDUP_KEY_DELIMITER = "\u001f"
private const val EMPTY_STACK_FALLBACK = "unknown"

internal fun extractLastFrameName(stacktrace: String): String {
	if (stacktrace.isBlank()) {
		return EMPTY_STACK_FALLBACK
	}

	val lines = stacktrace.lines()
	for (index in lines.indices.reversed()) {
		val line = lines[index].trim()
		if (!line.startsWith("at ")) {
			continue
		}
		val openParen = line.indexOf('(')
		if (openParen <= 3) {
			continue
		}
		val functionName = line.substring(3, openParen).trim()
		val slash = functionName.indexOf('/')
		val normalized =
			if (slash >= 0 && slash < functionName.lastIndex) {
				functionName.substring(slash + 1)
			} else {
				functionName
			}
		if (normalized.isNotEmpty()) {
			return normalized
		}
	}
	return EMPTY_STACK_FALLBACK
}

internal fun computeDedupKey(name: String?, message: String, stacktrace: String): String {
	val frameName = extractLastFrameName(stacktrace)
	return "${name.orEmpty()}$DEDUP_KEY_DELIMITER$message$DEDUP_KEY_DELIMITER$frameName"
}

internal class DedupCache {
	private val entries = ConcurrentHashMap<String, Long>()

	fun shouldSend(key: String, now: Long): Boolean {
		val firstSentAt = entries[key] ?: return true
		return now - firstSentAt >= DEDUP_WINDOW_MS
	}

	fun recordSend(key: String, now: Long) {
		val firstSentAt = entries[key]
		if (firstSentAt == null || now - firstSentAt >= DEDUP_WINDOW_MS) {
			entries[key] = now
		}
	}

	fun clear() {
		entries.clear()
	}
}
