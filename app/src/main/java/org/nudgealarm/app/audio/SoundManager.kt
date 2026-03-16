package org.nudgealarm.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import org.nudgealarm.app.R

/**
 * Manages UI sound effects for the retro game theme.
 */
object SoundManager {
    private var soundPool: SoundPool? = null
    private var soundClick: Int = 0
    private var soundMenuSelect: Int = 0
    private var soundSuccess: Int = 0
    private var soundBack: Int = 0
    private var soundStart: Int = 0
    private var soundCancel: Int = 0
    private var isInitialized = false
    private var soundEnabled = true

    fun initialize(context: Context) {
        if (isInitialized) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.let { pool ->
            soundClick = pool.load(context, R.raw.sfx_click, 1)
            soundMenuSelect = pool.load(context, R.raw.sfx_menu_select, 1)
            soundSuccess = pool.load(context, R.raw.sfx_success, 1)
            soundBack = pool.load(context, R.raw.sfx_back, 1)
            soundStart = pool.load(context, R.raw.sfx_start, 1)
            soundCancel = pool.load(context, R.raw.sfx_cancel, 1)
        }

        isInitialized = true
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun isSoundEnabled(): Boolean = soundEnabled

    /**
     * Play a short click sound for button presses.
     */
    fun playClick() {
        if (!soundEnabled) return
        soundPool?.play(soundClick, 0.5f, 0.5f, 1, 0, 1f)
    }

    /**
     * Play a menu selection sound.
     */
    fun playMenuSelect() {
        if (!soundEnabled) return
        soundPool?.play(soundMenuSelect, 0.6f, 0.6f, 1, 0, 1f)
    }

    /**
     * Play a success/done sound (coins).
     */
    fun playSuccess() {
        if (!soundEnabled) return
        soundPool?.play(soundSuccess, 0.7f, 0.7f, 1, 0, 1f)
    }

    /**
     * Play a back/navigation sound.
     */
    fun playBack() {
        if (!soundEnabled) return
        soundPool?.play(soundBack, 0.5f, 0.5f, 1, 0, 1f)
    }

    /**
     * Play a start game sound.
     */
    fun playStart() {
        if (!soundEnabled) return
        soundPool?.play(soundStart, 0.7f, 0.7f, 1, 0, 1f)
    }

    /**
     * Play a cancel/error sound.
     */
    fun playCancel() {
        if (!soundEnabled) return
        soundPool?.play(soundCancel, 0.5f, 0.5f, 1, 0, 1f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        isInitialized = false
    }
}
