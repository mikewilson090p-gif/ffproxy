package com.venice.ffproxy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private const val MAX = 600
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun log(tag: String, msg: String) {
        val line = fmt.format(Date()) + "  [" + tag + "]  " + msg
        synchronized(buf) {
            buf.addFirst(line)
            while (buf.size > MAX) buf.removeLast()
            _lines.value = buf.toList()
        }
    }

    fun clear() {
        synchronized(buf) {
            buf.clear()
            _lines.value = emptyList()
        }
    }
}