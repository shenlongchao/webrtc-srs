package com.webrtc.srs.iinterface

import android.content.Context
import com.webrtc.srs.bean.StreamType
import com.webrtc.srs.bean.VideoPeer
import org.webrtc.SurfaceViewRenderer

/**
 * Created by shen on 2023/7/29
 */
interface IRTCEngine {
    /**
     * 初始化，必须先调用
     */
    fun init(context: Context, uid: String)

    /**
     * 退出页面，销毁相关数据缓存
     */
    fun unInit()

    /**
     *开始推流，根据type区分音视频类型
     */
    fun startPublish(uid: String, streamType: StreamType, videoPeer: VideoPeer)

    /**
     *停止推流，根据type区分音视频类型
     */
    fun stopPush()

    /**
     *开始拉流，根据type区分音视频类型
     */
    fun startSubscribe(uid: String, streamType: StreamType, videoPeer: VideoPeer)

    /**
     *停止拉流
     */
    fun stopSubscribe(uid: String)

    /**
     * 切换摄像头
     */
    fun switchCamera(surfaceViewRenderer: SurfaceViewRenderer)

    /**
     * 设置自己音频开关
     */
    fun muteSelfAudio(isMute: Boolean)

    /**
     * 设置自己视频开关
     */
    fun muteSelfVideo(isMute: Boolean)

    /**
     * 是否开启扬声器
     */
    fun enableSpeakerPhone(enable: Boolean)

    /**
     * 设置加解密
     */
//    fun setEncryptionCallback(encryptionCallback: IEncryptionCallback)

    /**
     * 设置开始推流时的分辨率，必须在开始视频推流前设置
     */
    fun setRTCConfig(rtcConfig: RTCConfig)

    /**
     * 更改推流分辨率，在视频推流成功后设置
     */
    fun changeCaptureFormat(rtcConfig: RTCConfig)
}