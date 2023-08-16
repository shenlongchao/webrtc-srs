package com.webrtc.srs

import android.Manifest
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.request.ExplainScope
import com.permissionx.guolindev.request.ForwardScope
import com.webrtc.srs.bean.ChannelType
import com.webrtc.srs.bean.StreamType
import com.webrtc.srs.bean.VideoPeer
import com.webrtc.srs.iinterface.RTCConfig
import com.webrtc.srs.util.StringUtil
import com.webrtc.srs.databinding.ActivityMainBinding
import java.util.UUID

/**
 * @Author: 15652
 * @Time: 2023/8/10 11:04
 * @Description: 功能描述
 */
class MainActivity : AppCompatActivity() {

    private val permissions: ArrayList<String> = arrayListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initView()
        initListener()
        requestPermissions()

        EngineImpl.instance.init(this, uidMe)
        EngineImpl.instance.initViewAttribute(binding.renderView1)
        EngineImpl.instance.initViewAttribute(binding.renderView2)
    }

    private fun initView() {
        binding.apply {
            ed1.setText("webrtc://192.168.1.161/live/livestream")
            ed2.setText("webrtc://192.168.1.161/live/livestream")
        }
    }

    private fun initListener() {
        binding.apply {
            btnSwitchAudioMode.setOnClickListener {
                it.isSelected = !it.isSelected
                EngineImpl.instance.enableSpeakerPhone(it.isSelected)
                toast("isSpeakerphoneOn = ${it.isSelected}")
            }
            btnSwitchCamera.setOnClickListener {
                EngineImpl.instance.switchCamera(binding.renderView1)
            }
            btnMuteAudio.setOnClickListener {
                it.isSelected = !it.isSelected
                EngineImpl.instance.muteSelfAudio(it.isSelected)
            }
            btnMuteVideo.setOnClickListener {
                it.isSelected = !it.isSelected
                EngineImpl.instance.muteSelfVideo(it.isSelected)
            }
            btnChangeCapture.setOnClickListener {
                it.isSelected = !it.isSelected
                val rtcConfig = RTCConfig().apply {
                    videoWidth = if (it.isSelected) 174 else 1080
                    videoHeight = if (it.isSelected) 144 else 720
                }
                EngineImpl.instance.changeCaptureFormat(rtcConfig)
            }
        }
    }

    private fun requestPermissions() {

        PermissionX.init(this).permissions(permissions)
            .onExplainRequestReason { scope: ExplainScope, deniedList: List<String>, beforeRequest: Boolean ->
                scope.showRequestReasonDialog(
                    deniedList, "即将申请的权限是程序必须依赖的权限", "我已明白"
                )
            }.onForwardToSettings { scope: ForwardScope, deniedList: List<String> ->
                scope.showForwardToSettingsDialog(
                    deniedList, "您需要去应用程序设置当中手动开启权限", "我已明白"
                )
            }.request { allGranted: Boolean, grantedList: List<String?>?, deniedList: List<String?> ->
                if (allGranted) {
                    toast("所有申请的权限都已通过")
                } else {
                    toast("您拒绝了如下权限")
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        EngineImpl.instance.unInit()
    }

    fun doPush(view: View) {
        doPush()
    }

    fun stopPush(view: View) {
        EngineImpl.instance.stopPush()
    }

    private fun doPush() {
        val text = binding.ed1.editableText.toString()
        if (TextUtils.isEmpty(text)) {
            toast("推流地址为空!")
            return
        }
        val serverIp = StringUtil.getIps(text)[0]
        val streamUrl = String.format(STREAM_URL, serverIp)
        val playUrl = String.format(API, serverIp)
        val pushUrl = playUrl.replace("play", "publish")
        val videoPeer = VideoPeer().also {
            it.userId = uidMe
            it.pushUrl = pushUrl
            it.playUrl = playUrl
            it.streamUrl = streamUrl
            it.channelType = ChannelType.PUBLISH
            it.surfaceViewRenderer = binding.renderView1
        }
        val streamType = if (binding.rbPushVideo.isChecked) StreamType.VIDEO else StreamType.AUDIO
        EngineImpl.instance.startPublish(uidMe, streamType, videoPeer)
    }

    fun doPlay(view: View) {
        doPlay()
    }

    private fun doPlay() {
        val text = binding.ed2.editableText.toString()
        if (TextUtils.isEmpty(text)) {
            toast("拉流地址为空!")
            return
        }
        val serverIp = StringUtil.getIps(text)[0]
        val streamUrl = String.format(STREAM_URL, serverIp)
        val playUrl = String.format(API, serverIp)
        val pushUrl = playUrl.replace("play", "publish")
        val videoPeer = VideoPeer().also {
            it.userId = uidHim
            it.pushUrl = pushUrl
            it.playUrl = playUrl
            it.streamUrl = streamUrl
            it.channelType = ChannelType.SUBSCRIBE
            it.surfaceViewRenderer = binding.renderView2
        }
        val streamType = if (binding.rbPlayVideo.isChecked) StreamType.VIDEO else StreamType.AUDIO
        EngineImpl.instance.startSubscribe(uidHim, streamType, videoPeer)
    }

    private fun toast(s: String) {
        Toast.makeText(applicationContext, s, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val uidMe = UUID.randomUUID().toString()
        private val uidHim = UUID.randomUUID().toString()
        private const val API = "http://%s:1985/rtc/v1/play/"
        private const val STREAM_URL = "webrtc://%s/live/livestream"
    }
}