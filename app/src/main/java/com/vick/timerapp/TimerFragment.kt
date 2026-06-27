package com.vick.timerapp

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class TimerFragment : Fragment() {

    private lateinit var tvTime: TextView
    private lateinit var npHours: NumberPicker
    private lateinit var npMinutes: NumberPicker
    private lateinit var npSeconds: NumberPicker
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnReset: Button
    private lateinit var layoutTimerButtons: LinearLayout
    private lateinit var btnStopAlert: Button
    private lateinit var chipVibrate: Chip
    private lateinit var chipSound: Chip
    private lateinit var chipBoth: Chip

    private var countDownTimer: CountDownTimer? = null
    private var remainingMs: Long = 0L
    private var state = State.IDLE

    private val alertHandler = Handler(Looper.getMainLooper())
    private lateinit var soundPool: SoundPool
    private var soundId: Int = 0
    private var soundReady = false
    private var activeStreamId: Int = 0
    private var vibrator: Vibrator? = null

    private enum class State { IDLE, RUNNING, PAUSED }
    private enum class AlertMode { VIBRATE, SOUND, BOTH }

    private val alertMode: AlertMode
        get() = when {
            chipSound.isChecked -> AlertMode.SOUND
            chipBoth.isChecked -> AlertMode.BOTH
            else -> AlertMode.VIBRATE
        }

    private val alertRunnable = object : Runnable {
        override fun run() {
            fireAlert()
            alertHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_timer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTime = view.findViewById(R.id.tvTime)
        npHours = view.findViewById(R.id.npHours)
        npMinutes = view.findViewById(R.id.npMinutes)
        npSeconds = view.findViewById(R.id.npSeconds)
        btnStart = view.findViewById(R.id.btnStart)
        btnPause = view.findViewById(R.id.btnPause)
        btnReset = view.findViewById(R.id.btnReset)
        layoutTimerButtons = view.findViewById(R.id.layoutTimerButtons)
        btnStopAlert = view.findViewById(R.id.btnStopAlert)
        chipVibrate = view.findViewById(R.id.chipVibrate)
        chipSound = view.findViewById(R.id.chipSound)
        chipBoth = view.findViewById(R.id.chipBoth)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool.setOnLoadCompleteListener { _, _, status -> soundReady = (status == 0) }
        soundId = soundPool.load(requireContext(), R.raw.beep_09, 1)

        setupPickers()
        syncDisplayToPickers()
        updateButtons()

        btnStart.setOnClickListener { handleStart() }
        btnPause.setOnClickListener { handlePause() }
        btnReset.setOnClickListener { onReset() }
        btnStopAlert.setOnClickListener { stopAlert() }
    }

    private fun setupPickers() {
        val hourValues = Array(24) { String.format("%02d", it) }
        val minSecValues = Array(60) { String.format("%02d", it) }

        npHours.minValue = 0
        npHours.maxValue = 23
        npHours.displayedValues = hourValues

        npMinutes.minValue = 0
        npMinutes.maxValue = 59
        npMinutes.displayedValues = minSecValues

        npSeconds.minValue = 0
        npSeconds.maxValue = 59
        npSeconds.displayedValues = minSecValues.copyOf()

        val onChange = NumberPicker.OnValueChangeListener { _, _, _ ->
            if (state == State.IDLE) syncDisplayToPickers()
        }
        npHours.setOnValueChangedListener(onChange)
        npMinutes.setOnValueChangedListener(onChange)
        npSeconds.setOnValueChangedListener(onChange)
    }

    private fun pickerMs(): Long =
        (npHours.value * 3600L + npMinutes.value * 60L + npSeconds.value) * 1000L

    private fun syncDisplayToPickers() = updateDisplay(pickerMs())

    private fun handleStart() {
        val ms = if (state == State.IDLE) pickerMs() else remainingMs
        if (ms <= 0L) return
        state = State.RUNNING
        startCountDown(ms)
        setPickersEnabled(false)
        updateButtons()
    }

    private fun handlePause() {
        countDownTimer?.cancel()
        state = State.PAUSED
        updateButtons()
    }

    private fun onReset() {
        countDownTimer?.cancel()
        state = State.IDLE
        remainingMs = 0L
        setPickersEnabled(true)
        syncDisplayToPickers()
        updateButtons()
    }

    private fun startCountDown(ms: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(ms, 100L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                updateDisplay(millisUntilFinished)
            }
            override fun onFinish() {
                remainingMs = 0L
                updateDisplay(0L)
                state = State.IDLE
                setPickersEnabled(true)
                updateButtons()
                startAlert()
            }
        }.start()
    }

    // ── Alert ──────────────────────────────────────────────────────────────

    private fun startAlert() {
        layoutTimerButtons.visibility = View.GONE
        btnStopAlert.visibility = View.VISIBLE
        fireAlert()
        alertHandler.postDelayed(alertRunnable, 1000L)
    }

    private fun stopAlert() {
        alertHandler.removeCallbacks(alertRunnable)
        soundPool.stop(activeStreamId)
        vibrator?.cancel()
        btnStopAlert.visibility = View.GONE
        layoutTimerButtons.visibility = View.VISIBLE
    }

    private fun fireAlert() {
        val mode = alertMode
        if (mode == AlertMode.VIBRATE || mode == AlertMode.BOTH) doVibrate()
        if (mode == AlertMode.SOUND || mode == AlertMode.BOTH) doSound()
    }

    private fun doVibrate() {
        val effect = VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator?.vibrate(effect)
    }

    private fun doSound() {
        if (soundReady) {
            soundPool.stop(activeStreamId)
            activeStreamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private fun updateDisplay(ms: Long) {
        val total = ms / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        tvTime.text = String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun updateButtons() {
        when (state) {
            State.IDLE -> {
                setEnabled(btnStart, true)
                setEnabled(btnPause, false)
                setEnabled(btnReset, false)
            }
            State.RUNNING -> {
                setEnabled(btnStart, false)
                setEnabled(btnPause, true)
                setEnabled(btnReset, true)
            }
            State.PAUSED -> {
                setEnabled(btnStart, true)
                setEnabled(btnPause, false)
                setEnabled(btnReset, true)
            }
        }
    }

    private fun setEnabled(btn: Button, enabled: Boolean) {
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.35f
    }

    private fun setPickersEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.4f
        listOf(npHours, npMinutes, npSeconds).forEach {
            it.isEnabled = enabled
            it.alpha = alpha
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        alertHandler.removeCallbacks(alertRunnable)
        soundPool.release()
        vibrator?.cancel()
    }
}
