package com.vick.timerapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StopwatchFragment : Fragment() {

    private lateinit var tvTime: TextView
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnLap: Button
    private lateinit var btnReset: Button
    private lateinit var rvLaps: RecyclerView

    private val handler = Handler(Looper.getMainLooper())
    private val laps = mutableListOf<LapItem>()
    private lateinit var lapAdapter: LapAdapter

    private var startMs: Long = 0L
    private var elapsedMs: Long = 0L
    private var lastLapMs: Long = 0L
    private var isRunning = false

    private val ticker = object : Runnable {
        override fun run() {
            tvTime.text = formatMs(currentMs())
            handler.postDelayed(this, 16L)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_stopwatch, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvTime = view.findViewById(R.id.tvSwTime)
        btnStart = view.findViewById(R.id.btnSwStart)
        btnPause = view.findViewById(R.id.btnSwPause)
        btnLap = view.findViewById(R.id.btnSwLap)
        btnReset = view.findViewById(R.id.btnSwReset)
        rvLaps = view.findViewById(R.id.rvLaps)

        lapAdapter = LapAdapter(laps)
        rvLaps.layoutManager = LinearLayoutManager(requireContext())
        rvLaps.adapter = lapAdapter

        tvTime.text = "00:00.00"
        updateButtons()

        btnStart.setOnClickListener { start() }
        btnPause.setOnClickListener { pause() }
        btnLap.setOnClickListener { lap() }
        btnReset.setOnClickListener { reset() }
    }

    private fun start() {
        startMs = SystemClock.elapsedRealtime()
        isRunning = true
        handler.post(ticker)
        updateButtons()
    }

    private fun pause() {
        elapsedMs = currentMs()
        isRunning = false
        handler.removeCallbacks(ticker)
        updateButtons()
    }

    private fun lap() {
        val total = currentMs()
        val lapMs = total - lastLapMs
        lastLapMs = total
        laps.add(0, LapItem(laps.size + 1, lapMs, total))
        lapAdapter.notifyItemInserted(0)
        rvLaps.scrollToPosition(0)
    }

    private fun reset() {
        isRunning = false
        handler.removeCallbacks(ticker)
        elapsedMs = 0L
        lastLapMs = 0L
        startMs = 0L
        tvTime.text = "00:00.00"
        val count = laps.size
        laps.clear()
        lapAdapter.notifyItemRangeRemoved(0, count)
        updateButtons()
    }

    private fun currentMs(): Long =
        if (isRunning) elapsedMs + (SystemClock.elapsedRealtime() - startMs) else elapsedMs

    private fun updateButtons() {
        val hasTime = elapsedMs > 0L || isRunning
        setEnabled(btnStart, !isRunning)
        setEnabled(btnPause, isRunning)
        setEnabled(btnLap, isRunning)
        setEnabled(btnReset, !isRunning && hasTime)
    }

    private fun setEnabled(btn: Button, enabled: Boolean) {
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.35f
    }

    private fun formatMs(ms: Long): String {
        val h = ms / 3600000L
        val m = (ms % 3600000L) / 60000L
        val s = (ms % 60000L) / 1000L
        val cs = (ms % 1000L) / 10L
        return if (h > 0) String.format("%02d:%02d:%02d.%02d", h, m, s, cs)
        else String.format("%02d:%02d.%02d", m, s, cs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(ticker)
    }
}
