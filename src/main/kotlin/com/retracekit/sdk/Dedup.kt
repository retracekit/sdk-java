package com.retracekit.sdk

import java.util.concurrent.ConcurrentHashMap

internal const val DEDUP_WINDOW_MS = 30_000L
internal const val DEDUP_MAX_ENTRIES = 1000
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

/**
 * Thread-safe dedup window: check + record are atomic via [ConcurrentHashMap.compute].
 * Stale entries (older than [DEDUP_WINDOW_MS]) are pruned on each acquire; if still over
 * [DEDUP_MAX_ENTRIES], oldest entries (by timestamp) are evicted until size is within the cap.
 *
 * Call [release] if a reserved send was not actually accepted by transport, so the key can
 * be retried immediately.
 */
internal class DedupCache {
	private val entries = ConcurrentHashMap<String, Long>()

	/**
	 * Returns true if this key may be sent now, and records the send timestamp atomically.
	 * Returns false if the key is still within the dedup window.
	 */
	fun tryAcquire(key: String, now: Long): Boolean {
		pruneExpired(now)
		val allowed = booleanArrayOf(false)
		entries.compute(key) { _, firstSentAt ->
			if (firstSentAt == null || now - firstSentAt >= DEDUP_WINDOW_MS) {
				allowed[0] = true
				now
			} else {
				firstSentAt
			}
		}
		if (entries.size > DEDUP_MAX_ENTRIES) {
			pruneExpired(now)
			evictOldestUntilUnderMax()
		}
		return allowed[0]
	}

	/** Drop a reservation so the same key can be acquired again (e.g. transport rejected the send). */
	fun release(key: String) {
		entries.remove(key)
	}

	fun sizeForTesting(): Int = entries.size

	fun clear() {
		entries.clear()
	}

	private fun pruneExpired(now: Long) {
		entries.entries.removeIf { (_, firstSentAt) -> now - firstSentAt >= DEDUP_WINDOW_MS }
	}

	private fun evictOldestUntilUnderMax() {
		while (entries.size > DEDUP_MAX_ENTRIES) {
			var oldestKey: String? = null
			var oldestTs = Long.MAX_VALUE
			for ((key, firstSentAt) in entries) {
				if (firstSentAt < oldestTs) {
					oldestTs = firstSentAt
					oldestKey = key
				}
			}
			val key = oldestKey ?: break
			entries.remove(key, oldestTs)
		}
	}
}
