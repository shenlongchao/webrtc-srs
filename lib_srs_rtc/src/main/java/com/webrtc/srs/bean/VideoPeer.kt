package com.webrtc.srs.bean

import org.webrtc.AudioTrack
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class VideoPeer {
    var userId: String = ""
    var pushUrl: String = ""
    var playUrl: String = ""
    var streamUrl: String = ""
    var channelType: ChannelType = ChannelType.PUBLISH
    var peerConnection: PeerConnection? = null
    var surfaceViewRenderer: SurfaceViewRenderer? = null
    var videoTrack: VideoTrack? = null
    var audioTrack: AudioTrack? = null

    fun release() {
        userId = ""
        peerConnection?.dispose()
        peerConnection = null
//        surfaceViewRenderer?.release()
//        surfaceViewRenderer?.clearImage()
//        videoTrack?.dispose()
//        audioTrack?.dispose()
    }
}