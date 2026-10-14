package com.venice.ffproxy

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RulesStore {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules

    fun init(ctx: Context) {
        prefs = ctx.applicationContext
            .getSharedPreferences("ffproxy_store", Context.MODE_PRIVATE)
        val raw = prefs.getString("rules", null)
        _rules.value = if (raw.isNullOrBlank()) {
            defaults()
        } else {
            runCatching {
                gson.fromJson(raw, Array<Rule>::class.java)?.toList()
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: defaults()
        }
        persist()
    }

    fun current(): List<Rule> = _rules.value

    fun setEnabled(id: String, on: Boolean) {
        _rules.value = _rules.value.map { if (it.id == id) it.copy(enabled = on) else it }
        persist()
    }

    fun update(rule: Rule) {
        _rules.value = _rules.value.map { if (it.id == rule.id) rule else it }
        persist()
    }

    fun resetToDefaults() {
        _rules.value = defaults()
        persist()
    }

    private fun persist() {
        runCatching { prefs.edit().putString("rules", gson.toJson(_rules.value)).apply() }
    }

    private fun fpsRegex() =
        "(?i)^(fps|max_?fps|target_?fps|frame_?rate|max_?frame_?rate|fps_?level|fps_?limit|refresh_?rate|max_?refresh_?rate|fps_?cap)$"

    private fun defaults(): List<Rule> = listOf(
        Rule(
            id = "fps120", name = "Forcar 120 FPS", enabled = true,
            type = RuleType.JSON_PATCH,
            contentTypeRegex = "(?i).*(json|javascript|text).*",
            keyRegex = fpsRegex(),
            value = "120"
        ),
        Rule(
            id = "fps90", name = "Forcar 90 FPS", enabled = false,
            type = RuleType.JSON_PATCH,
            contentTypeRegex = "(?i).*(json|javascript|text).*",
            keyRegex = fpsRegex(),
            value = "90"
        ),
        Rule(
            id = "hfr", name = "Liberar HFR / alta taxa", enabled = true,
            type = RuleType.JSON_PATCH,
            contentTypeRegex = "(?i).*(json|javascript|text).*",
            keyRegex = "(?i)^(hfr|high_?refresh_?rate|high_?fps|support_?hfr|supports_?hfr|enable_?hfr)$",
            value = "true"
        ),
        Rule(
            id = "gfx", name = "Graficos no maximo", enabled = false,
            type = RuleType.JSON_PATCH,
            contentTypeRegex = "(?i).*(json|javascript|text).*",
            keyRegex = "(?i)^(graphics_?quality|graphic_?level|quality_?level|gfx_?level|render_?quality|graphics_?level)$",
            value = "4"
        ),
        Rule(
            id = "aim", name = "Aim assist ligado (config)", enabled = false,
            type = RuleType.JSON_PATCH,
            contentTypeRegex = "(?i).*(json|javascript|text).*",
            keyRegex = "(?i)^(aim_?assist|auto_?aim|aim_?lock|aim_?assist_?strength)$",
            value = "true"
        ),
        Rule(
            id = "nocache", name = "Sem cache (ver mudanca na hora)", enabled = false,
            type = RuleType.HEADER_SET,
            urlRegex = ".*",
            headerName = "Cache-Control",
            headerValue = "no-cache, no-store, must-revalidate"
        ),
        Rule(
            id = "injectjs", name = "Injetar script em JS/HTML", enabled = false,
            type = RuleType.BODY_APPEND,
            contentTypeRegex = "(?i).*(javascript|ecmascript|html).*",
            snippet = "\n/*FFProxy*/console.log('[FFProxy] payload injetado');\n"
        )
    )
}