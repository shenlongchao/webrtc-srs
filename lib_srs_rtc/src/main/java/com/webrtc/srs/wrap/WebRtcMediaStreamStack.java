package com.webrtc.srs.wrap;

import android.content.Context;
import android.util.Log;

import org.jetbrains.annotations.Nullable;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

public class WebRtcMediaStreamStack {
    private static final String TAG = "WebRtcMediaStreamStack";

    private PeerConnectionFactory factory;

    private Context appContext;
    private EglBase appEglBase;
    private VideoCapturer videoCapturer;

    @Nullable
    private AudioSource audioSource;
    @Nullable
    private VideoSource videoSource;
    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;

    @Nullable
    private VideoTrack localVideoTrack;
    @Nullable
    private AudioTrack localAudioTrack;

    private MediaStreamParameters mediaStreamParameters;

    private CameraEnumerator cameraEnumerator;

    private boolean isFrontCamera = false;

    public static class MediaStreamParameters {
        public final boolean videoCallEnabled;
        public final int videoWidth;
        public final int videoHeight;
        public final int videoFps;
        public VideoSink localVideoRender;
        public final boolean muteVideo;
        public final boolean muteAudio;

        public final String userId;

        public MediaStreamParameters(String userId, boolean videoCallEnabled, int videoWidth, int videoHeight, int videoFps,
                                     VideoSink localVideoRender, boolean muteVideo, boolean muteAudio) {
            this.userId = userId;
            this.videoCallEnabled = videoCallEnabled;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.localVideoRender = localVideoRender;
            this.muteVideo = muteVideo;
            this.muteAudio = muteAudio;
        }
    }

    public WebRtcMediaStreamStack(PeerConnectionFactory factory, Context appContext, EglBase appEglBase, MediaStreamParameters mediaStreamParameters) {
        this.factory = factory;
        this.appContext = appContext;
        this.appEglBase = appEglBase;
        this.mediaStreamParameters = mediaStreamParameters;
    }

    public boolean startMediaStream() {
        if (factory == null) {
            Log.e(TAG, "startMediaStream factory is null");
            return false;
        }

        if (mediaStreamParameters.videoCallEnabled) {
            videoCapturer = createVideoCapturer();
            if (videoCapturer == null) {
                Log.e(TAG, "startMediaStream VideoCapturer is null");
                return false;
            }
            createVideoTrack();
        }
        createAudioTrack();
        return true;
    }

    private VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        if (Camera2Enumerator.isSupported(this.appContext)) {
            Log.d(TAG, "createVideoCapturer using camera2 API");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this.appContext));
        } else {
            Log.d(TAG, "createVideoCapturer using camera1 API");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private @Nullable VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        cameraEnumerator = enumerator;
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "createCameraCapturer looking for front facing cameras");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "createCameraCapturer creating front facing camera capturer");
                isFrontCamera = true;
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "createCameraCapturer looking for other cameras");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "createCameraCapturer creating other camera capturer");
                isFrontCamera = false;
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private VideoTrack createVideoTrack() {
        surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", this.appEglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, this.appContext, videoSource.getCapturerObserver());
        videoCapturer.startCapture(mediaStreamParameters.videoWidth, mediaStreamParameters.videoHeight, mediaStreamParameters.videoFps);

        localVideoTrack = factory.createVideoTrack(mediaStreamParameters.userId, videoSource);
        localVideoTrack.setEnabled(!mediaStreamParameters.muteVideo);
        if (mediaStreamParameters.localVideoRender != null) {
            localVideoTrack.addSink(mediaStreamParameters.localVideoRender);
        }
        return localVideoTrack;
    }

    private AudioTrack createAudioTrack() {
        audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack(mediaStreamParameters.userId, audioSource);
        localAudioTrack.setEnabled(!mediaStreamParameters.muteAudio);
        return localAudioTrack;
    }

    public VideoTrack getVideoTrack() {
        return localVideoTrack;
    }

    public AudioTrack getAudioTrack() {
        return localAudioTrack;
    }

    public VideoCapturer getVideoCapturer() {
        return videoCapturer;
    }

    public void muteAudio(final boolean mute) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!mute);
        }
    }

    public void muteVideo(final boolean mute) {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(!mute);
        }
    }

    public void changeCaptureFormat(final int width, final int height, final int frameRate) {
        Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + frameRate);
        if (videoCapturer != null && videoSource != null) {
            videoSource.adaptOutputFormat(width, height, frameRate);
        }
    }

    public void switchCamera() {
        Log.d(TAG, "switchCamera");
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            if (cameraEnumerator != null) {
                for (String deviceName : cameraEnumerator.getDeviceNames()) {
                    if (isFrontCamera && cameraEnumerator.isBackFacing(deviceName)) {
                        cameraVideoCapturer.switchCamera(null, deviceName);
                        isFrontCamera = false;
                        break;
                    } else if (!isFrontCamera && cameraEnumerator.isFrontFacing(deviceName)) {
                        cameraVideoCapturer.switchCamera(null, deviceName);
                        isFrontCamera = true;
                        break;
                    }
                }
            }
        } else {
            Log.e(TAG, "switchCamera will not switch camera, video caputurer is not a camera");
        }
    }

    public void dispose() {
        Log.d(TAG, "closing audio source");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        Log.d(TAG, "stopping capture");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        Log.d(TAG, "closing video source");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }

        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }

        mediaStreamParameters = null;

        Log.d(TAG, "closing source done");
    }
}
