package moe.chensi.volume.data

import kotlin.math.ln
import kotlin.math.pow

/**
 * Maps slider position (0..1, what the user sees) to linear gain (0..1, what IPlayer.setVolume wants).
 *
 * Human loudness perception is logarithmic: -10 dB sounds roughly "half as loud".
 * With a 40 dB range every quarter of the slider is ~-10 dB, so the slider feels even
 * along its whole length instead of doing everything in the bottom 15%.
 * The (10^x - 1) form keeps 0 exactly silent without a jump near the bottom.
 */
object VolumeCurve {
    const val CURRENT_VERSION = 1

    private const val RANGE_DB = 40f
    private val maxRatio = 10f.pow(RANGE_DB / 20f)

    fun positionToGain(position: Float): Float {
        val p = position.coerceIn(0f, 1f)
        if (p <= 0f) return 0f
        if (p >= 1f) return 1f
        return (maxRatio.pow(p) - 1f) / (maxRatio - 1f)
    }

    fun gainToPosition(gain: Float): Float {
        val g = gain.coerceIn(0f, 1f)
        if (g <= 0f) return 0f
        if (g >= 1f) return 1f
        return (ln(g * (maxRatio - 1f) + 1f) / ln(maxRatio)).coerceIn(0f, 1f)
    }
}
