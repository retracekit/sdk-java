package com.retracekit.sdk.internal

internal class JsonRaw(val raw: String)

internal object Json {
	fun obj(vararg pairs: Pair<String, Any?>): String {
		val parts =
			pairs.mapNotNull { (key, value) ->
				if (value == null) {
					null
				} else {
					"${quote(key)}:${encode(value)}"
				}
			}
		return "{${parts.joinToString(",")}}"
	}

	fun encode(value: Any?): String =
		when (value) {
			null -> "null"
			is JsonRaw -> value.raw
			is String -> quote(value)
			is Number -> value.toString()
			is Boolean -> value.toString()
			is Map<*, *> -> {
				val parts =
					value.entries.mapNotNull { (k, v) ->
						if (k == null || v == null) {
							null
						} else {
							"${quote(k.toString())}:${encode(v)}"
						}
					}
				"{${parts.joinToString(",")}}"
			}
			is List<*> -> "[${value.joinToString(",") { encode(it) }}]"
			else -> quote(value.toString())
		}

	private fun quote(raw: String): String {
		val escaped =
			buildString(raw.length + 2) {
				append('"')
				for (ch in raw) {
					when (ch) {
						'\\' -> append("\\\\")
						'"' -> append("\\\"")
						'\n' -> append("\\n")
						'\r' -> append("\\r")
						'\t' -> append("\\t")
						else -> {
							if (ch.code < 0x20) {
								append("\\u")
								append(ch.code.toString(16).padStart(4, '0'))
							} else {
								append(ch)
							}
						}
					}
				}
				append('"')
			}
		return escaped
	}
}
