package moe.chensi.volume.calls

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import moe.chensi.volume.data.AppSettings

/**
 * Pauses all playing media when a call (phone or VoIP) becomes active and keeps it paused
 * afterwards: the pause is sent like a user tap, and for a few seconds after the call any
 * app that tries to resume on its own is paused again.
 */
class CallMediaPauser(private val context: Context, private val settings: AppSettings) {
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)!!
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var inCall = false
    /** Apps that were playing when the phone started ringing (they may be ducked/paused now). */
    private val playingBeforeCall = mutableSetOf<String>()
    /** Apps we paused; guarded against auto-resume right after the call. */
    private val pausedByUs = mutableSetOf<String>()

    private val modeListener = AudioManager.OnModeChangedListener { mode -> onMode(mode) }

    fun start() {
        audioManager.addOnModeChangedListener(context.mainExecutor, modeListener)
        onMode(audioManager.mode)
    }

    fun stop() {
        runCatching { audioManager.removeOnModeChangedListener(modeListener) }
        handler.removeCallbacksAndMessages(null)
    }

    private fun controllers(): List<MediaController> = try {
        sessionManager.getActiveSessions(listener)
    } catch (e: SecurityException) {
        Log.w(TAG, "No notification-listener access, can't control media", e)
        emptyList()
    }

    private fun MediaController.isPlaying() = when (playbackState?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_CONNECTING -> true
        else -> false
    }

    private fun onMode(mode: Int) {
        if (!settings.callPauseMedia.value) {
            inCall = false
            return
        }

        val ringing = mode == AudioManager.MODE_RINGTONE
        val talking = mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_CALL_SCREENING
        val callActive = talking || (ringing && settings.callPauseOnRing.value)

        if (ringing && !inCall) {
            // Remember who was playing before Android ducks/pauses them for the ringtone
            playingBeforeCall.clear()
            controllers().filter { it.isPlaying() }.mapTo(playingBeforeCall) { it.packageName }
        }

        if (callActive && !inCall) {
            inCall = true
            handler.removeCallbacksAndMessages(null)
            pauseAll()
        } else if (mode == AudioManager.MODE_NORMAL) {
            if (inCall) guardAfterCall()
            inCall = false
            playingBeforeCall.clear()
        }
    }

    private fun pauseAll() {
        for (c in controllers()) {
            if (c.isPlaying() || c.packageName in playingBeforeCall) {
                Log.i(TAG, "Call started, pausing ${c.packageName}")
                c.transportControls.pause()
                pausedByUs += c.packageName
            }
        }
    }

    /** Some apps resume by themselves when the call ends; pause them again. */
    private fun guardAfterCall() {
        if (pausedByUs.isEmpty()) return
        for (delayMs in longArrayOf(800, 2000, 4000)) {
            handler.postDelayed({
                for (c in controllers()) {
                    if (c.packageName in pausedByUs && c.isPlaying()) {
                        Log.i(TAG, "Call ended, ${c.packageName} resumed by itself, pausing again")
                        c.transportControls.pause()
                    }
                }
            }, delayMs)
        }
        handler.postDelayed({ pausedByUs.clear() }, 4500)
    }

    companion object {
        private const val TAG = "CallMediaPauser"
    }
}
