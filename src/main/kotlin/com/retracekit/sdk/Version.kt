package com.retracekit.sdk

/**
 * SDK version from classpath resource written by Gradle ([retrace-kit-version.txt]),
 * with a hardcoded fallback for IDEs / unexpected classpaths.
 */
internal val VERSION: String by lazy { loadVersion() }

private fun loadVersion(): String {
	return try {
		val stream =
			RetraceKit::class.java.getResourceAsStream("/retrace-kit-version.txt")
				?: return "0.1.1"
		stream.bufferedReader().use { it.readText().trim() }.ifEmpty { "0.1.1" }
	} catch (_: Throwable) {
		"0.1.1"
	}
}
