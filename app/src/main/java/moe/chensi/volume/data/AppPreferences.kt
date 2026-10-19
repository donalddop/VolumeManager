package moe.chensi.volume.data

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    var isPlayer: Boolean = false,
    var volume: Float = 1.0f,
    var hidden: Boolean = false,
    var disableVolumeButtons: Boolean = false,
    // 0 = `volume` stored as linear gain (upstream), 1 = stored as perceptual slider position
    var curveVersion: Int = 0
)