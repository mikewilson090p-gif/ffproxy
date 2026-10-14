package com.venice.ffproxy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class RuleAdapter(
    private val onToggle: (Rule, Boolean) -> Unit,
    private val onEdit: (Rule) -> Unit
) : RecyclerView.Adapter<RuleAdapter.VH>() {

    private var items: List<Rule> = emptyList()

    fun submit(list: List<Rule>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val detail: TextView = v.findViewById(R.id.tvDetail)
        val sw: MaterialSwitch = v.findViewById(R.id.swRule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rule = items[position]
        holder.name.text = rule.name
        holder.detail.text = rule.detail()
        holder.sw.setOnCheckedChangeListener(null)
        holder.sw.isChecked = rule.enabled
        holder.sw.setOnCheckedChangeListener { _, checked -> onToggle(rule, checked) }
        holder.itemView.setOnClickListener { onEdit(rule) }
    }
}