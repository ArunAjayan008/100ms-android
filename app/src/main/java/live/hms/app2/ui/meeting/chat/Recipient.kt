package live.hms.app2.ui.meeting.chat

import live.hms.video.sdk.models.HMSPeer
import live.hms.video.sdk.models.role.HMSRole

sealed class Recipient {
    object Everyone : Recipient() {
        override fun toString(): String = "Everyone"
    }
    data class Role(val role : HMSRole) : Recipient() {
        override fun toString(): String =
            role.name
    }
    data class Peer(val peer : HMSPeer) : Recipient() {
        override fun toString(): String = peer.name
    }
}