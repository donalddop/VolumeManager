package moe.chensi.volume.system

import android.media.AudioAttributes
import android.media.AudioPlaybackConfiguration
import android.os.DeadObjectException
import org.joor.Reflect
import org.joor.ReflectException
import java.lang.reflect.InvocationTargetException

class AudioPlaybackConfigurationProxy(raw: AudioPlaybackConfiguration) {
    enum class PlayerState(val value: Int) {
        Unknown(-1), Released(0), Idle(1), Started(2), Paused(3), Stopped(4);
    }

    fun Int.toPlayerState(): PlayerState {
        for (state in PlayerState.entries) {
            if (state.value == this) {
                return state
            }
        }
        return PlayerState.Unknown
    }

    companion object {
        private val AUDIBLE_USAGES = setOf(
            AudioAttributes.USAGE_UNKNOWN,
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME,
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.USAGE_ASSISTANT,
        )

        val classReflect: Reflect = Reflect.onClass(AudioPlaybackConfiguration::class.java)
    }

    private val reflect = Reflect.on(raw)

    /** AudioAttributes usage (public API): media, game, notification, alarm, ... */
    val usage: Int = runCatching { raw.audioAttributes.usage }.getOrDefault(AudioAttributes.USAGE_UNKNOWN)

    private val player = reflect.call("getIPlayer")

    val hasPlayer
        get() = player.get<Any>() != null

    val clientPid: Int = reflect.get("mClientPid")

    val clientUid: Int = runCatching { reflect.get<Int>("mClientUid") }.getOrDefault(-1)

    val playerType: Int = reflect.get("mPlayerType")

    val playerTypeName: String by lazy {
        classReflect.call("toLogFriendlyPlayerType", playerType).get()
    }

    val playerState
        get() = reflect.get<Int>("mPlayerState").toPlayerState()

    val playerStateName: String by lazy {
        classReflect.call("playerStateToString", playerState.value).get()
    }

    /**
     * "Really making sound you'd want a slider for": started right now, not a SoundPool
     * (fire-and-forget effects that never report state) and a media-like usage.
     * Notification pings, key clicks, alarms and ringtones don't count.
     */
    val isAudible: Boolean
        get() = playerType != 3 && playerState == PlayerState.Started && usage in AUDIBLE_USAGES

    val isPlaying: Boolean
        get() {
            if (playerType == 3) {
                return true
            }

            return playerState == PlayerState.Started
        }

    // Look the method up once instead of on every call (jOOR re-resolves each time).
    private val setVolumeMethod: java.lang.reflect.Method? by lazy {
        player.get<Any>()?.javaClass?.runCatching {
            getMethod("setVolume", Float::class.javaPrimitiveType)
        }?.getOrNull()
    }

    fun setVolume(value: Float): Boolean {
        val target = player.get<Any>() ?: return false
        val method = setVolumeMethod
        if (method != null) {
            return try {
                method.invoke(target, value)
                true
            } catch (e: InvocationTargetException) {
                if (e.cause is DeadObjectException) false else throw e
            }
        }
        return try {
            player.call("setVolume", value)
            true
        } catch (e: ReflectException) {
            val cause = e.cause
            if (cause is InvocationTargetException && cause.cause is DeadObjectException) {
                false
            } else {
                throw e
            }
        }
    }
}
