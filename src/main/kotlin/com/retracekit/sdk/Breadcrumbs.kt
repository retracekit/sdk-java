package com.retracekit.sdk

private const val MAX_BREADCRUMBS = 24

internal object Breadcrumbs {
	private val lock = Any()
	private val queue = ArrayDeque<Breadcrumb>(MAX_BREADCRUMBS)

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
			synchronized(lock) {
				if (queue.size >= MAX_BREADCRUMBS) {
					queue.removeFirst()
				}
				queue.addLast(crumb)
			}
		} catch (_: Throwable) {
			// Never throw into host application code.
		}
	}

	fun snapshot(): List<Breadcrumb> = synchronized(lock) { queue.toList() }

	fun resetForTesting() {
		synchronized(lock) {
			queue.clear()
		}
	}
}
