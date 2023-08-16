package com.webrtc.srs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.text.TextUtils
import android.util.Log
import com.google.gson.GsonBuilder
import com.webrtc.srs.api.ApiInterface
import com.webrtc.srs.bean.ChannelType
import com.webrtc.srs.bean.PlayBodyBean
import com.webrtc.srs.bean.SdpBean
import com.webrtc.srs.bean.StreamType
import com.webrtc.srs.bean.VideoPeer
import com.webrtc.srs.iinterface.IRTCEngine
import com.webrtc.srs.iinterface.RTCConfig
import com.webrtc.srs.util.StringUtil
import com.webrtc.srs.wrap.WebRtcMediaStreamStack
import com.webrtc.srs.wrap.WebRtcPeerConnectionFactoryStack
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.RtpTransceiver.RtpTransceiverInit
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.voiceengine.WebRtcAudioUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Created by shen on 2023/7/28
 */
class EngineImpl private constructor() : IRTCEngine {

    private var mPeerConnectionFactory: PeerConnectionFactory? = null
    private var mLocalStream: WebRtcMediaStreamStack? = null
    private var isMirror = true
    private var mContext: Context? = null
    private var mUid: String? = null
    private val mPeerMap = ConcurrentHashMap<String, VideoPeer>()
    private var mRtcConfig: RTCConfig = RTCConfig()

    override fun init(context: Context, uid: String) {
        mUid = uid
        mContext = context
        WebRtcPeerConnectionFactoryStack.init(context)
        mPeerConnectionFactory = WebRtcPeerConnectionFactoryStack.getInstance().peerConnectionFactroy
    }

    override fun unInit() {
        stopPush()
        mPeerConnectionFactory?.dispose()
        mPeerConnectionFactory = null
        while (mPeerMap.elements().hasMoreElements()) {
            mPeerMap.elements().nextElement().surfaceViewRenderer?.release()
        }
        mPeerMap.clear()
        WebRtcPeerConnectionFactoryStack.dispose()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
    }

    override fun startPublish(uid: String, streamType: StreamType, videoPeer: VideoPeer) {
        mPeerMap[uid] = videoPeer

        if (mLocalStream == null) {
            mLocalStream = videoPeer.surfaceViewRenderer?.let { createLocalStream(it, streamType) }
        }
        val peerConnection = createPeerConnection(uid)
        peerConnection?.let {
            initPublish(it, streamType)
            mLocalStream?.apply {
                it.addTrack(audioTrack)
                if (videoTrack != null && streamType == StreamType.VIDEO) {
                    it.addTrack(videoTrack)
                }
            }

            val mediaConstraints = MediaConstraints()
            it.createOffer(InnerSdpObserver(uid), mediaConstraints)
            mPeerMap[uid]?.apply {
                this.peerConnection = it
            }
        }
    }

    override fun stopPush() {
        mLocalStream?.dispose()
        mLocalStream = null
        mPeerMap[mUid]?.release()
//        if (mPeerMap.contains(mUid)) {
//            mPeerMap.remove(mUid)
//        }
    }

    override fun startSubscribe(uid: String, streamType: StreamType, videoPeer: VideoPeer) {
        mPeerMap[videoPeer.userId] = videoPeer

        val peerConnection = createPeerConnection(uid)
        peerConnection?.let {
            initSubScribe(it, streamType)

            for (transceiver in it.transceivers) {
                val receiver = transceiver.receiver
                val track = receiver.track()
                if (track is VideoTrack) {
                    track.addSink(videoPeer.surfaceViewRenderer)
                }
            }

            val mediaConstraints = MediaConstraints()
            it.createOffer(InnerSdpObserver(uid), mediaConstraints)
            mPeerMap[uid]?.apply {
                this.peerConnection = it
            }
        }
    }

    override fun stopSubscribe(uid: String) {
        mPeerMap[uid]?.release()
    }

    private fun createLocalStream(surfaceViewRenderer: SurfaceViewRenderer, streamType: StreamType): WebRtcMediaStreamStack {
        val parameters = WebRtcMediaStreamStack.MediaStreamParameters(
            mUid,
            streamType == StreamType.VIDEO,
            mRtcConfig.videoWidth,
            mRtcConfig.videoHeight,
            mRtcConfig.fps,
            null,
            false,
            false
        )
        val localStream = WebRtcMediaStreamStack(
            mPeerConnectionFactory,
            mContext,
            WebRtcPeerConnectionFactoryStack.getInstance().appEglBase,
            parameters
        ).also {
            it.startMediaStream()
            it.videoTrack?.addSink(surfaceViewRenderer)
            mPeerMap[mUid]?.apply {
                videoTrack = it.videoTrack
                audioTrack = it.audioTrack
            }
        }
        return localStream
    }

    private fun createPeerConnection(uid: String?): PeerConnection? {
        val peerConnection = mPeerConnectionFactory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: SignalingState) {}
            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {}
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                mPeerMap[uid]?.peerConnection?.addIceCandidate(iceCandidate)
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                mPeerMap[uid]?.peerConnection?.removeIceCandidates(iceCandidates)
            }

            override fun onAddStream(mediaStream: MediaStream) {

            }

            override fun onRemoveStream(mediaStream: MediaStream) {}
            override fun onDataChannel(dataChannel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {

            }
        })

        return peerConnection
    }

    fun initViewAttribute(renderer: SurfaceViewRenderer) {
        renderer.init(WebRtcPeerConnectionFactoryStack.getInstance().appEglBase.eglBaseContext, null)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        renderer.setEnableHardwareScaler(true)
        renderer.setZOrderMediaOverlay(true)
    }

    private fun getOkHttpClient(): OkHttpClient {
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS) //设置读取超时时间
            .writeTimeout(10, TimeUnit.SECONDS) //设置写的超时时间
            .connectTimeout(10, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            val httpLoggingInterceptor = HttpLoggingInterceptor()
            builder.addInterceptor(httpLoggingInterceptor.apply {
                httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            })
        }
        return builder.build()
    }

    @SuppressLint("CheckResult")
    fun openWebRtc(sdp: String, uid: String) {
        val playBodyBean = PlayBodyBean()
        var pushOrPlayUrl: String? = null
        mPeerMap[uid]?.apply {
            pushOrPlayUrl = if (channelType == ChannelType.PUBLISH) pushUrl else playUrl
            playBodyBean.api = pushOrPlayUrl
            playBodyBean.clientip = StringUtil.getIpAddressString()
            playBodyBean.streamurl = streamUrl
            playBodyBean.sdp = sdp
        }

        val gson = GsonBuilder()
            .setLenient()
            .create()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://wsx.bctec01.shop/")
            .client(getOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson)) //设置使用Gson解析(记得加入依赖)
            .build()

        Log.e(TAG, "gson: " + gson.toJson(playBodyBean))

        val request = retrofit.create(ApiInterface::class.java)
        val call = request.webrtcPlay(pushOrPlayUrl ?: "", playBodyBean)
        call.enqueue(object : Callback<SdpBean> {
            override fun onResponse(call: Call<SdpBean>, response: Response<SdpBean>) {
                val gson = GsonBuilder().disableHtmlEscaping().create()
                var s = gson.toJson(response.body())
                s = s.replace("\n".toRegex(), "")
                Log.e(TAG, "s: $s")
                if (!TextUtils.isEmpty(s)) {
                    val sdpBean = gson.fromJson(s, SdpBean::class.java)
                    if (sdpBean.code == 400) {
//                        openWebRtc(sdp, uid)
                        return
                    }
                    if (!TextUtils.isEmpty(sdpBean.sdp)) {
                        setRemoteSdp(sdpBean.sdp, uid)
                    }
                }
            }

            override fun onFailure(call: Call<SdpBean>, throwable: Throwable) {
            }
        })
    }

    private fun setRemoteSdp(sdp: String, uid: String) {
//        Log.e(TAG, "setRemoteSdp: ");
        val peerConnection = mPeerMap[uid]?.peerConnection
        if (peerConnection != null) {
            val remoteSpd = SessionDescription(SessionDescription.Type.ANSWER, sdp)
//            Log.e(TAG, "setRemoteDescription: ");
            peerConnection.setRemoteDescription(InnerSdpObserver(uid), remoteSpd)
        }
    }

    internal inner class InnerSdpObserver(var uid: String) : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {
            if (sessionDescription.type == SessionDescription.Type.OFFER) {
                //设置setLocalDescription offer返回sdp
                mPeerMap[uid]?.peerConnection?.setLocalDescription(this, sessionDescription)
                if (!TextUtils.isEmpty(sessionDescription.description)) {
                    openWebRtc(sessionDescription.description, uid)
                }
            }
        }

        override fun onSetSuccess() {}
        override fun onCreateFailure(s: String) {}
        override fun onSetFailure(s: String) {}
    }

    /**
     * 设置仅推送音视频
     */
    private fun initPublish(peerConnection: PeerConnection, type: StreamType) {
        peerConnection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
        )
//        if (type == StreamType.VIDEO) {
        peerConnection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
        )
//        }
        //设置回声去噪
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true)
    }

    /**
     * 设置仅接收音视频
     */
    private fun initSubScribe(peerConnection: PeerConnection, type: StreamType) {
        peerConnection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        if (type == StreamType.VIDEO) {
            peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
        }
    }

    //关闭分辨率变换
    //修改模式 PlanB无法使用仅接收音视频的配置
    private val config: RTCConfiguration
        get() {
            val rtcConfig = RTCConfiguration(ArrayList())
//            rtcConfig.cryptoOptions = CryptoOptions.builder().setRequireFrameEncryption(true).createCryptoOptions()
            //关闭分辨率变换
            rtcConfig.enableCpuOveruseDetection = false
            //修改模式 PlanB无法使用仅接收音视频的配置
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            return rtcConfig
        }

    override fun switchCamera(surfaceViewRenderer: SurfaceViewRenderer) {
        mLocalStream?.switchCamera()
        isMirror = !isMirror
        surfaceViewRenderer.setMirror(isMirror)
    }

    override fun muteSelfAudio(isMute: Boolean) {
        mLocalStream?.muteAudio(isMute)
    }

    override fun muteSelfVideo(isMute: Boolean) {
        mLocalStream?.muteVideo(isMute)
    }

    override fun enableSpeakerPhone(enable: Boolean) {
        val audioManager = mContext?.getSystemService(Activity.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
            AudioManager.FX_KEY_CLICK
        )
        audioManager.isSpeakerphoneOn = enable
    }

    override fun setRTCConfig(rtcConfig: RTCConfig) {
        mRtcConfig = rtcConfig
    }

    override fun changeCaptureFormat(rtcConfig: RTCConfig) {
        mLocalStream?.videoCapturer?.also {
            mRtcConfig = rtcConfig
            it.changeCaptureFormat(rtcConfig.videoWidth, rtcConfig.videoHeight, rtcConfig.fps)
        }
    }

    companion object {
        const val TAG = "EngineImpl"

        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            EngineImpl()
        }
    }
}