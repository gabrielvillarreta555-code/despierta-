package com.example.despierta

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

internal enum class TonePreviewState {
    IDLE,
    PREPARING,
    PLAYING,
    PLAYING_FALLBACK,
    FAILED
}

/**
 * Short, user-initiated alarm-tone preview. It owns audio focus and every player it creates,
 * retries inaccessible custom URIs with Android defaults, and never loops indefinitely.
 */
internal class AlarmTonePreview(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var generation = 0
    private var callback: ((TonePreviewState) -> Unit)? = null

    fun play(soundUri: String, onStateChanged: (TonePreviewState) -> Unit) {
        releasePlayback()
        generation++
        val currentGeneration = generation
        callback = onStateChanged
        onStateChanged(TonePreviewState.PREPARING)

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener(
                { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS) stop()
                },
                handler
            )
            .build()
        focusRequest = request
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            AlarmLog.warning("tone_preview_focus_denied", "reason=audio_focus_unavailable")
            releasePlayback()
            onStateChanged(TonePreviewState.FAILED)
            callback = null
            return
        }

        val candidates = listOfNotNull(
            soundUri.takeIf(String::isNotBlank)?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).distinctBy(Uri::toString)
        playCandidate(candidates, 0, currentGeneration)
        handler.postDelayed(
            {
                if (generation == currentGeneration) stop()
            },
            MAX_PREVIEW_MILLIS
        )
    }

    fun stop() {
        val notify = callback
        generation++
        releasePlayback()
        callback = null
        notify?.invoke(TonePreviewState.IDLE)
    }

    private fun playCandidate(candidates: List<Uri>, index: Int, expectedGeneration: Int) {
        if (generation != expectedGeneration) return
        if (index >= candidates.size) {
            AlarmLog.error("tone_preview_unavailable", "reason=no_playable_uri")
            releasePlayback()
            callback?.invoke(TonePreviewState.FAILED)
            callback = null
            return
        }

        val uri = candidates[index]
        val candidate = MediaPlayer()
        player = candidate
        val configured = runCatching {
            candidate.setAudioAttributes(AUDIO_ATTRIBUTES)
            candidate.setDataSource(appContext, uri)
            candidate.isLooping = false
            candidate.setOnPreparedListener { prepared ->
                if (generation != expectedGeneration || player !== prepared) {
                    runCatching { prepared.release() }
                    return@setOnPreparedListener
                }
                runCatching { prepared.start() }
                    .onSuccess {
                        callback?.invoke(
                            if (index == 0) TonePreviewState.PLAYING
                            else TonePreviewState.PLAYING_FALLBACK
                        )
                        AlarmLog.info("tone_preview_started", "fallback=${index > 0} uri=$uri")
                    }
                    .onFailure {
                        AlarmLog.warning("tone_preview_start_failed", "uri=$uri", it)
                        retryCandidate(candidate, candidates, index, expectedGeneration)
                    }
            }
            candidate.setOnCompletionListener {
                if (generation == expectedGeneration) stop()
            }
            candidate.setOnErrorListener { failed, what, extra ->
                AlarmLog.warning(
                    "tone_preview_media_error",
                    "uri=$uri what=$what extra=$extra"
                )
                retryCandidate(failed, candidates, index, expectedGeneration)
                true
            }
            candidate.prepareAsync()
        }.onFailure {
            AlarmLog.warning("tone_preview_uri_failed", "uri=$uri", it)
        }.isSuccess

        if (!configured) retryCandidate(candidate, candidates, index, expectedGeneration)
    }

    private fun retryCandidate(
        failed: MediaPlayer,
        candidates: List<Uri>,
        index: Int,
        expectedGeneration: Int
    ) {
        runCatching { failed.reset() }
        runCatching { failed.release() }
        if (player === failed) player = null
        playCandidate(candidates, index + 1, expectedGeneration)
    }

    private fun releasePlayback() {
        player?.let { active ->
            runCatching { active.stop() }
            runCatching { active.reset() }
            runCatching { active.release() }
        }
        player = null
        focusRequest?.let { request ->
            runCatching { audioManager.abandonAudioFocusRequest(request) }
        }
        focusRequest = null
    }

    companion object {
        private const val MAX_PREVIEW_MILLIS = 10_000L
        private val AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
