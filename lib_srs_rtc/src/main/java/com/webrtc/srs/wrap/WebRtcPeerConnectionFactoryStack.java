package com.webrtc.srs.wrap;

import android.content.Context;
import android.util.Log;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStateCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackStateCallback;

public class WebRtcPeerConnectionFactoryStack {
  final static String TAG = "WebRtcPeerConnectionFactoryStack";
  private static volatile WebRtcPeerConnectionFactoryStack instance;

  private static Context appContext;
  private static EglBase appEglBase;

  private static PeerConnectionFactory factory;

  private WebRtcPeerConnectionFactoryStack() {
    createPeerConnectionFactory();
  }

  public static WebRtcPeerConnectionFactoryStack getInstance() {
    if (instance == null) {
      synchronized (WebRtcPeerConnectionFactoryStack.class) {
        if (instance == null) {
          instance = new WebRtcPeerConnectionFactoryStack();
		}
	  }
	}
	return instance;
  }

  private void createPeerConnectionFactory() {
    appEglBase = EglBase.create();
    final AudioDeviceModule adm = createJavaAudioDevice();
    final VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(this.appEglBase.getEglBaseContext(), true, true);
    final VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(this.appEglBase.getEglBaseContext());

    factory = PeerConnectionFactory.builder()
                  .setAudioDeviceModule(adm)
                  .setVideoEncoderFactory(encoderFactory)
                  .setVideoDecoderFactory(decoderFactory)
                  .createPeerConnectionFactory();
    Log.d(TAG, "Peer connection factory created.");
    adm.release();
  }

  AudioDeviceModule createJavaAudioDevice() {
    // Set audio record error callbacks.
    AudioRecordErrorCallback audioRecordErrorCallback = new AudioRecordErrorCallback() {
      @Override
      public void onWebRtcAudioRecordInitError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
      }

      @Override
      public void onWebRtcAudioRecordStartError(
          JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
      }

      @Override
      public void onWebRtcAudioRecordError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
      }
    };

    AudioTrackErrorCallback audioTrackErrorCallback = new AudioTrackErrorCallback() {
      @Override
      public void onWebRtcAudioTrackInitError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
      }

      @Override
      public void onWebRtcAudioTrackStartError(
          JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
      }

      @Override
      public void onWebRtcAudioTrackError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
      }
    };

    // Set audio record state callbacks.
    AudioRecordStateCallback audioRecordStateCallback = new AudioRecordStateCallback() {
      @Override
        public void onWebRtcAudioRecordStart() {
        Log.i(TAG, "Audio recording starts");
      }

      @Override
      public void onWebRtcAudioRecordStop() {
        Log.i(TAG, "Audio recording stops");
      }
    };

    // Set audio track state callbacks.
    AudioTrackStateCallback audioTrackStateCallback = new AudioTrackStateCallback() {
      @Override
      public void onWebRtcAudioTrackStart() {
        Log.i(TAG, "Audio playout starts");
      }

      @Override
      public void onWebRtcAudioTrackStop() {
        Log.i(TAG, "Audio playout stops");
      }
    };

    return JavaAudioDeviceModule.builder(this.appContext)
        .setAudioRecordErrorCallback(audioRecordErrorCallback)
        .setAudioTrackErrorCallback(audioTrackErrorCallback)
        .setAudioRecordStateCallback(audioRecordStateCallback)
        .setAudioTrackStateCallback(audioTrackStateCallback)
        // .setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX)
        .createAudioDeviceModule();
  }

  public static void init(Context context/*, EglBase eglBase*/) {
    appContext = context;
	// appEglBase = eglBase;

    String fieldTrials = getFieldTrials();

    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(appContext)
            .setFieldTrials(fieldTrials)
            .setEnableInternalTracer(true)
            .createInitializationOptions());

  }

  public static void dispose() {
    Log.d(TAG,"factory dispose");
    if (factory != null) {
      factory.dispose();
	  factory = null;
    }
    appEglBase.release();
    appEglBase = null;
    PeerConnectionFactory.stopInternalTracingCapture();
    PeerConnectionFactory.shutdownInternalTracer();
    instance = null;
  }

  private static String getFieldTrials() {
    String fieldTrials = "";
    if (true) {
      // fieldTrials += "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
      Log.d(TAG, "Enable FlexFEC field trial.");
    }
    return fieldTrials;
  }

  public PeerConnectionFactory getPeerConnectionFactroy() {
    return factory;
  }

  public Context getAppContext() {
    return this.appContext;
  }

  public EglBase getAppEglBase() {
    return this.appEglBase;
  }
}
