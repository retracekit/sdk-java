package com.retracekit.sdk

internal object Handlers {
	private val lock = Any()

	@Volatile
	private var previousHandler: Thread.UncaughtExceptionHandler? = null

	@Volatile
	private var installedHandler: Thread.UncaughtExceptionHandler? = null

	fun install() {
		synchronized(lock) {
			try {
				val current = Thread.getDefaultUncaughtExceptionHandler()
				if (installedHandler != null && current === installedHandler) {
					return
				}
				// Always wrap whatever is currently installed so foreign handlers are preserved.
				previousHandler = current
				val previous = previousHandler
				val handler =
					Thread.UncaughtExceptionHandler { thread, throwable ->
						try {
							Capture.captureException(throwable, flushSync = true)
						} catch (_: Throwable) {
							// ignore
						}
						try {
							previous?.uncaughtException(thread, throwable)
						} catch (_: Throwable) {
							// ignore
						}
					}
				installedHandler = handler
				Thread.setDefaultUncaughtExceptionHandler(handler)
			} catch (_: Throwable) {
				// Never throw into host application code.
			}
		}
	}

	fun uninstall() {
		synchronized(lock) {
			try {
				val installed = installedHandler
				if (installed != null && Thread.getDefaultUncaughtExceptionHandler() === installed) {
					Thread.setDefaultUncaughtExceptionHandler(previousHandler)
				}
				previousHandler = null
				installedHandler = null
			} catch (_: Throwable) {
				// Never throw into host application code.
			}
		}
	}

	/** Package-visible hook for tests. */
	fun installedHandlerForTesting(): Thread.UncaughtExceptionHandler? = installedHandler

	fun resetForTesting() {
		uninstall()
	}
}
