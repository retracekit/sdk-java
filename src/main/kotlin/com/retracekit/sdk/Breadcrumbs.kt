package com.retracekit.sdk

import java.util.concurrent.CopyOnWriteArrayList

private const val MAX_BREADCRUMBS = 24

internal object Breadcrumbs {
	private val queue = CopyOnWriteArrayList<Breadcrumb>()

	fun add(
		type: BreadcrumbType,
		name: String,
		value: String,
		status: Int? = null,
		duration: Int? = null,
	) {
		try {
			val crumb =
				Breadcrumb(
					type = type,
					name = name,
					value = value,
					capturedAt = utcNowIso(),
					status = status,
					duration = duration,
				)
			queue.add(crumb)
			while (queue.size > MAX_BREADCRUMBS) {
				queue.removeAt(0)
			}
		} catch (_: Exception) {
			// Never throw into host application code.
		}
	}

	fun snapshot(): List<Breadcrumb> = queue.toList()

	fun resetForTesting() {
		queue.clear()
	}
}
