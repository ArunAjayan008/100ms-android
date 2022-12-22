package live.hms.app2.ui.meeting.pinnedvideo

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import live.hms.app2.util.Truss
import live.hms.video.connection.stats.*
import live.hms.video.media.settings.HMSLayer
import live.hms.video.media.tracks.HMSAudioTrack
import live.hms.video.media.tracks.HMSTrack
import live.hms.video.media.tracks.HMSVideoTrack
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class StatsInterpreter(val active: Boolean) {
    // For this to happen in n time rather than n^x times for a video, they'll have to
    // register with the central flow and receive events during the iteration.

    fun initiateStats(
        lifecycleOwner: LifecycleOwner,
        itemStats: Flow<Map<String, Any>>,
        currentVideoTrack: HMSVideoTrack?,
        currentAudioTrack: HMSAudioTrack?,
        isLocal: Boolean,
        setText: (CharSequence) -> Unit
    ) {
        if (active) {
            lifecycleOwner.lifecycleScope.launch {

                itemStats.map { allStats ->

                    val relevantStats = mutableListOf<HMSStats?>().apply {
                        add(allStats[currentAudioTrack?.trackId] as? HMSStats)
                        add(allStats[currentVideoTrack?.trackId] as? HMSStats)
                        (allStats[currentVideoTrack?.trackId] as? List<*>)?.forEach { simulcastVideoTrack ->
                            add(simulcastVideoTrack as? HMSStats)
                        }
                    }
                    return@map (relevantStats.filterNotNull())
                }
                    .map {
                        it.fold("" as CharSequence) { acc, webrtcStats ->
                            val out = when (webrtcStats) {
                                is HMSRemoteAudioStats -> buildSpannedString {
                                    append("\nAudio")
                                    appendLine()
                                    append("Jitter:${webrtcStats.jitter}")
                                    appendLine()
                                    append("Bitrate:${webrtcStats.bitrate?.roundToInt()}")
                                    appendLine()
                                    append("PL:${webrtcStats.packetsLost}")
                                    appendLine()
                                }
                                is HMSRemoteVideoStats -> buildSpannedString {
                                    append("\nVideo")
                                    appendLine()
                                    append("Jitter:${webrtcStats.jitter}")
                                    appendLine()
                                    append("PL:${webrtcStats.packetsLost}\n")
                                    appendLine()
                                    append("FPS:${webrtcStats.frameRate}")
                                    appendLine()
                                    append("Width:${webrtcStats.resolution?.width}")
                                    appendLine()
                                    append("Height:${webrtcStats.resolution?.height}")
                                    appendLine()
                                }
                                is HMSLocalAudioStats -> buildSpannedString {
                                    append("\nLocalAudio")
                                    appendLine()
                                    append("Bitrate: ${webrtcStats.bitrate?.roundToInt()}")
                                    appendLine()
                                }
                                is HMSLocalVideoStats -> buildSpannedString {
                                    append("\nLocalVideo")
                                    appendLine()
                                    bold { append("Quality:${webrtcStats.hmsLayer}") }
                                    appendLine()
                                    append("Bitrate: ${webrtcStats.bitrate?.roundToInt()}")
                                    appendLine()
                                    append("Width:${webrtcStats.resolution?.width}")
                                    appendLine()
                                    append("Height:${webrtcStats.resolution?.height}")
                                    appendLine()
                                    append("QualityLimitation:${webrtcStats.qualityLimitationReason.reason}")
                                    appendLine()
                                    append("QLBandwith:${webrtcStats.qualityLimitationReason.bandWidth}")
                                    appendLine()
                                    append("QLCPU:${webrtcStats.qualityLimitationReason.cpu}")
                                    appendLine()
                                    append("QLNone:${webrtcStats.qualityLimitationReason.none}")
                                    appendLine()
                                    append("QLOther:${webrtcStats.qualityLimitationReason.other}")
                                    appendLine()
                                }

                                else -> acc
                            }

                             TextUtils.concat(out, acc)
                        }
                    }
                    .collect {
                        withContext(Dispatchers.Main) {
                            setText(it)
                        }
                    }


            }
        }
    }


}