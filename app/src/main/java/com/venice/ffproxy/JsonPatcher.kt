package com.venice.ffproxy

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

object JsonPatcher {

    fun patchText(text: String, keyRegex: String, newValue: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null

        val root = try {
            JsonParser.parseString(trimmed)
        } catch (t: Throwable) {
            return null
        }
        if (!root.isJsonObject && !root.isJsonArray) return null

        val rx = try {
            Regex(keyRegex, RegexOption.IGNORE_CASE)
        } catch (t: Throwable) {
            return null
        }

        return if (walk(root, rx, newValue)) root.toString() else null
    }

    private fun walk(el: JsonElement, rx: Regex, v: String): Boolean {
        var changed = false
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            for (key in obj.keySet().toList()) {
                val child = obj.get(key) ?: continue
                if (child.isJsonPrimitive && rx.matches(key)) {
                    obj.add(key, toPrimitive(v))
                    changed = true
                } else if (child.isJsonObject || child.isJsonArray) {
                    if (walk(child, rx, v)) changed = true
                }
            }
        } else if (el.isJsonArray) {
            for (child in el.asJsonArray) {
                if (walk(child, rx, v)) changed = true
            }
        }
        return changed
    }

    private fun toPrimitive(v: String): JsonPrimitive {
        if (v.equals("true", true) || v.equals("false", true)) {
            return JsonPrimitive(v.toBoolean())
        }
        val d = v.toDoubleOrNull()
        if (d != null && !d.isNaN() && !d.isInfinite()) {
            return if (d == Math.floor(d)) JsonPrimitive(d.toLong()) else JsonPrimitive(d)
        }
        return JsonPrimitive(v)
    }
}