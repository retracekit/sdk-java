package com.retracekit.sdk

internal object Handlers {
	@Volatile
	private var previousHandler: Thread.UncaughtExceptionHandler? = null

	@Volatile
	private var installedHandler: Thread.UncaughtExceptionHandler? = null

	fun install() {
		try {
			val current = Thread.getDefaultUncaughtExceptionHandler()
			if (installedHandler != null && current === installedHandler) {
				return
			}
			previousHandler = current
			val previous = previousHandler
			val handler =
				Thread.UncaughtExceptionHandler { thread, throwable ->
					try {
						Capture.captureException(throwable)
					} catch (_: Exception) {
						// ignore
					}
					try {
						previous?.uncaughtException(thread, throwable)
					} catch (_: Exception) {
						// ignore
					}
				}
			installedHandler = handler
			Thread.setDefaultUncaughtExceptionHandler(handler)
		} catch (_: Exception) {
			// Never throw into host application code.
		}
	}

	fun uninstall() {
		try {
			val installed = installedHandler
			if (installed != null && Thread.getDefaultUncaughtExceptionHandler() === installed) {
				Thread.setDefaultUncaughtExceptionHandler(previousHandler)
			}
			previousHandler = null
			installedHandler = null
		} catch (_: Exception) {
			// Never throw into host application code.
		}
	}

	fun resetForTesting() {
		uninstall()
	}
}
