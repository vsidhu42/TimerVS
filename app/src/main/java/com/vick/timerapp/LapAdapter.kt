package com.vick.timerapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class LapItem(val number: Int, val lapMs: Long, val totalMs: Long)

class LapAdapter(private val laps: List<LapItem>) : RecyclerView.Adapter<LapAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvLapNumber)
        val tvDuration: TextView = view.findViewById(R.id.tvLapDuration)
        val tvTotal: TextView = view.findViewById(R.id.tvLapTotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_lap, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lap = laps[position]
        holder.tvNumber.text = "Lap ${lap.number}"
        holder.tvDuration.text = formatMs(lap.lapMs)
        holder.tvTotal.text = formatMs(lap.totalMs)
    }

    override fun getItemCount() = laps.size

    private fun formatMs(ms: Long): String {
        val h = ms / 3600000L
        val m = (ms % 3600000L) / 60000L
        val s = (ms % 60000L) / 1000L
        val cs = (ms % 1000L) / 10L
        return if (h > 0) String.format("%02d:%02d:%02d.%02d", h, m, s, cs)
        else String.format("%02d:%02d.%02d", m, s, cs)
    }
}
