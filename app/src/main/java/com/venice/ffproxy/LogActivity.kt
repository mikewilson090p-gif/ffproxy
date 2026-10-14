package com.venice.ffproxy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        adapter = LogAdapter()
        val rv = findViewById<RecyclerView>(R.id.rvLog)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<Button>(R.id.btnClear).setOnClickListener { LogStore.clear() }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        lifecycleScope.launch {
            LogStore.lines.collectLatest { list ->
                adapter.submit(list)
                if (list.isNotEmpty()) rv.scrollToPosition(0)
            }
        }
    }

    class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {
        private var items: List<String> = emptyList()

        fun submit(l: List<String>) {
            items = l
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvLog)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_log, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, i: Int) {
            h.tv.text = items[i]
        }
    }
}