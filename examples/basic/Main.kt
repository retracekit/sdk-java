package com.example

import com.retracekit.sdk.Config
import com.retracekit.sdk.RetraceKit

/**
 * Minimal example. Set RETRACE_KIT_API_KEY to send a real event.
 *
 * Run from repo root after `./gradlew publishToMavenLocal` or with the project on the classpath.
 */
fun main() {
	val apiKey = System.getenv("RETRACE_KIT_API_KEY").orEmpty()
	RetraceKit.init(
		Config(
			apiKey = apiKey,
			endpoint =
				System.getenv("RETRACE_KIT_ENDPOINT")
					?: Config.DEFAULT_ENDPOINT,
			environment = System.getenv("RETRACE_KIT_ENVIRONMENT") ?: "dev",
			release = System.getenv("RETRACE_KIT_RELEASE") ?: "0.1.0",
			serverUrl = System.getenv("RETRACE_KIT_SERVER_URL"),
		),
	)

	RetraceKit.setTag("example", "basic")
	RetraceKit.setUser("demo-user")

	try {
		error("Retrace Kit Java SDK demo exception")
	} catch (error: Throwable) {
		RetraceKit.captureException(error)
		println("Captured exception (userAgent will be: Java ${Runtime.version()})")
		Thread.sleep(1_500)
	}
}
