# webrtc-srs-android

A aar for webrtc used in android.

简单代码实现webrtc在srs服务器下的音视频推拉流

## 初始化

初始化，必须先调用，以下各个方法都通过`EngineImpl`调用
`EngineImpl.instance.init(context: Context, uid: String)`

初始化view属性
`initViewAttribute(surfacerenderView)`

开始推流，根据type区分音视频类型
`startPublish(uid: String, streamType: StreamType, videoPeer: VideoPeer)`

停止推流
`stopPush()`

开始拉流
`startSubscribe(uidHim, streamType, videoPeer)`

停止拉流，根据type区分
`stopSubscribe(uid: String)`

退出页面，销毁相关数据缓存
`unInit()`

切换摄像头
`switchCamera(surfaceViewRenderer: SurfaceViewRenderer)`

设置自己音频开关
`muteSelfAudio(isMute: Boolean)`

设置自己视频开关
`muteSelfVideo(isMute: Boolean)`

是否开启扬声器
`enableSpeakerPhone(enable: Boolean)`

设置开始推流时的分辨率，必须在开始推流前设置
`setRTCConfig(rtcConfig: RTCConfig)`

更改推流分辨率，在视频推流成功后设置
`changeCaptureFormat(rtcConfig: RTCConfig)`
