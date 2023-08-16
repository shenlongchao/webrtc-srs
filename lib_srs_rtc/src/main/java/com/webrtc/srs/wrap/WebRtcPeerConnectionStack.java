package com.webrtc.srs.wrap;

import android.util.Log;

import org.jetbrains.annotations.Nullable;
import org.webrtc.AudioTrack;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.CryptoOptions;
import org.webrtc.DataChannel;
import org.webrtc.FrameDecryptor;
import org.webrtc.FrameEncryptor;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.PeerConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WebRtcPeerConnectionStack {
    private static final String TAG = "WebRtcPeerConnectionStack";
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private final Timer statsTimer = new Timer();

    private boolean dataChannelEnabled;
    @Nullable
    private DataChannel dataChannel;
    @Nullable
    private VideoTrack remoteVideoTrack;
    @Nullable
    private AudioTrack remoteAudioTrack;

    @Nullable
    private RtpSender localAudioSender;
    @Nullable
    private RtpSender localVideoSender;

    @Nullable
    private RtpReceiver remoteAudioReceiver;
    @Nullable
    private RtpReceiver remoteVideoReceiver;

    private PeerConnectionParameters peerConnectionParameters;

    private final PCObserver pcObserver = new PCObserver();

    public static class DataChannelParameters {
        public final boolean ordered;
        public final int maxRetransmitTimeMs;
        public final int maxRetransmits;
        public final String protocol;
        public final boolean negotiated;
        public final int id;

        public DataChannelParameters(boolean ordered, int maxRetransmitTimeMs, int maxRetransmits,
                                     String protocol, boolean negotiated, int id) {
            this.ordered = ordered;
            this.maxRetransmitTimeMs = maxRetransmitTimeMs;
            this.maxRetransmits = maxRetransmits;
            this.protocol = protocol;
            this.negotiated = negotiated;
            this.id = id;
        }
    }

    public static class PeerConnectionParameters {
        private final AudioTrack audioTrack;
        private final VideoTrack videoTrack;
        private final VideoSink remoteVideoRender;
        private boolean invite; // createOffer
        private final DataChannelParameters dataChannelParameters;

        public PeerConnectionParameters(AudioTrack audioTrack, VideoTrack videoTrack, VideoSink remoteVideoRender, boolean invite, DataChannelParameters dataChannelParameters) {
            this.audioTrack = audioTrack;
            this.videoTrack = videoTrack;
            this.remoteVideoRender = remoteVideoRender;
            this.invite = invite;
            this.dataChannelParameters = dataChannelParameters;
        }
    }

    public interface PeerConnectionEvents {
        void onLocalDescription(final SessionDescription sdp);

        void onIceCandidate(final IceCandidate candidate);

        default void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        }

        default void onIceConnected() {
        }

        default void onIceDisconnected() {
        }

        default void onConnected() {
        }

        default void onDisconnected() {
        }

        default void onPeerConnectionClosed() {
        }

        default void onPeerConnectionStatsReady(final RTCStatsReport report) {
        }

        default void onPeerConnectionError(final String description) {
        }

    }

    private final PeerConnectionEvents events;

    public WebRtcPeerConnectionStack(PeerConnectionFactory factory, PeerConnectionParameters peerConnectionParameters, PeerConnectionEvents events) {
        this.factory = factory;
        this.peerConnectionParameters = peerConnectionParameters;
        this.dataChannelEnabled = peerConnectionParameters.dataChannelParameters != null;
        this.events = events;

        createPeerConnectionInternal();
    }

    private void createPeerConnectionInternal() {
        if (factory == null) {
            Log.e(TAG, "createPeerConnectionInternal factory is null");
            return;
        }

        List<PeerConnection.IceServer> iceServerList = new ArrayList<>();
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServerList);
        rtcConfig.cryptoOptions = CryptoOptions.builder().setRequireFrameEncryption(true).createCryptoOptions();
        rtcConfig.enableCpuOveruseDetection = false;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peerConnection = factory.createPeerConnection(rtcConfig, pcObserver);
        if (peerConnection == null) {
            Log.e(TAG, "createPeerConnectionInternal pc failed");
            return;
        }

        if (peerConnectionParameters.audioTrack != null) {
            peerConnection.addTrack(peerConnectionParameters.audioTrack);
        }

        if (peerConnectionParameters.videoTrack != null) {
            peerConnection.addTrack(peerConnectionParameters.videoTrack);
        }
        initRemoteVideoAndAudioTrack();
        if (remoteVideoTrack != null) {
            remoteVideoTrack.addSink(peerConnectionParameters.remoteVideoRender);
        }

        if (dataChannelEnabled) {
            DataChannel.Init init = new DataChannel.Init();
            dataChannel = peerConnection.createDataChannel("data-channel", init);
        }

        findAudioVideoSender();

        if (peerConnectionParameters.invite) {
            createOffer();
        }
        peerConnection.setBitrate(300 * 1000, 700 * 1000, 1000 * 1000);
        enableStatsEvents(true, 1000);
    }

    public boolean addRemoteVideoRender(final List<VideoSink> sinks) {
        if (remoteVideoTrack == null) {
            return false;
        }
        for (VideoSink remoteSink : sinks) {
            remoteVideoTrack.addSink(remoteSink);
        }

        return true;
    }

    private void initRemoteVideoAndAudioTrack() {
        for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
            RtpReceiver receiver = transceiver.getReceiver();
            MediaStreamTrack track = receiver.track();
            if (track instanceof VideoTrack) {
                remoteVideoReceiver = receiver;
                remoteVideoTrack = (VideoTrack) track;
            } else if (track instanceof AudioTrack) {
                remoteAudioReceiver = receiver;
                remoteAudioTrack = (AudioTrack) track;
            }
        }
    }

    private @Nullable VideoTrack getRemoteVideoTrack() {
        for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
            RtpReceiver receiver = transceiver.getReceiver();
            MediaStreamTrack track = receiver.track();
            if (track instanceof VideoTrack) {
                remoteVideoReceiver = receiver;
                return (VideoTrack) track;
            }
        }
        return null;
    }

    private @Nullable AudioTrack getRemoteAudioTrack() {
        for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
            RtpReceiver receiver = transceiver.getReceiver();
            MediaStreamTrack track = receiver.track();
            if (track instanceof AudioTrack) {
                remoteAudioReceiver = receiver;
                Log.e(TAG, track + "   " + track.id() + "    " + remoteAudioReceiver + "  " + remoteAudioReceiver.id());
                return (AudioTrack) track;
            }
        }
        return null;
    }

    private void findAudioVideoSender() {
        for (RtpSender sender : peerConnection.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();
                if (trackType.equals("video")) {
                    localVideoSender = sender;
                } else if (trackType.equals("audio")) {
                    localAudioSender = sender;
                }
            }
        }
    }

    public boolean muteAudio(final boolean mute) {
        if (remoteAudioTrack != null) {
            remoteAudioTrack.setEnabled(!mute);
            return true;
        }

        return false;
    }

    public boolean muteVideo(final boolean mute) {
        if (remoteVideoTrack != null) {
            remoteVideoTrack.setEnabled(!mute);
            return true;
        }

        return false;
    }

    public void setFrameEncryptor(FrameEncryptor frameEncryptor) {
        if (localVideoSender != null) localVideoSender.setFrameEncryptor(frameEncryptor);
        if (localAudioSender != null) localAudioSender.setFrameEncryptor(frameEncryptor);
    }

    public void setFrameDecryptor(FrameDecryptor frameDecryptor) {
        if (remoteVideoReceiver != null) remoteVideoReceiver.setFrameDecryptor(frameDecryptor);
        if (remoteAudioReceiver != null) remoteAudioReceiver.setFrameDecryptor(frameDecryptor);
    }

    public boolean send(ByteBuffer byteBuffer, boolean binary) {
        if (dataChannel != null) {
            DataChannel.Buffer buffer = new DataChannel.Buffer(byteBuffer, binary);
            return dataChannel.send(buffer);
        }

        return false;
    }

    public void enableStatsEvents(boolean enable, int periodMs) {
        if (enable) {
            try {
                statsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        getStats();
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                Log.e(TAG, "enableStatsEvents exception", e);
            }
        } else {
            statsTimer.cancel();
        }
    }

    private void getStats() {
        if (peerConnection == null) {
            return;
        }
        peerConnection.getStats(new RTCStatsCollectorCallback() {
            @Override
            public void onStatsDelivered(RTCStatsReport report) {
                events.onPeerConnectionStatsReady(report);
            }
        });
    }

    // send side
    public void setBitrate(int min, int max) {
        if (peerConnection == null) return;
        int cur = min + (max - min) / 2;
        peerConnection.setBitrate(min * 1000, cur * 1000, max * 1000);
    }

    public int[] getBitrate(int videoWidth, int videoHeight, int frameRate) {
        int pixel_count = videoWidth * videoHeight * frameRate;
        int maxBitrate = 200;
        int minBitrate = 100;
        if (frameRate > 15) {
            if (pixel_count >= 1920 * 1080 * 25) {
                maxBitrate = 2000;
                minBitrate = 600;
            } else if (pixel_count >= 1440 * 1080 * 25) {
                maxBitrate = 1750;
                minBitrate = 600;
            } else if (pixel_count >= 1280 * 720 * 25) {
                maxBitrate = 1450;
                minBitrate = 600;
            } else if (pixel_count >= 960 * 720 * 25) {
                maxBitrate = 1300;
                minBitrate = 500;
            } else if (pixel_count >= 960 * 540 * 25) {
                maxBitrate = 950;
                minBitrate = 400;
            } else if (pixel_count >= 720 * 540 * 25) {
                maxBitrate = 900;
                minBitrate = 400;
            } else if (pixel_count >= 640 * 480 * 25) {
                maxBitrate = 800;
                minBitrate = 300;
            } else if (pixel_count >= 640 * 360 * 25) {
                maxBitrate = 500;
                minBitrate = 200;
            } else if (pixel_count >= 480 * 360 * 25) {
                maxBitrate = 450;
                minBitrate = 200;
            } else if (pixel_count >= 320 * 240 * 25) {
                maxBitrate = 350;
            } else if (pixel_count >= 320 * 180 * 25) {
                maxBitrate = 300;
            }
        } else {
            if (pixel_count >= 1920 * 1080 * 15) {
                maxBitrate = 1700;
            } else if (pixel_count >= 1440 * 1080 * 15) {
                maxBitrate = 1500;
            } else if (pixel_count >= 1280 * 720 * 15) {
                maxBitrate = 1200;
            } else if (pixel_count >= 960 * 720 * 15) {
                maxBitrate = 1050;
            } else if (pixel_count >= 960 * 540 * 15) {
                maxBitrate = 800;
                minBitrate = 300;
            } else if (pixel_count >= 720 * 540 * 15) {
                maxBitrate = 700;
                minBitrate = 300;
            } else if (pixel_count >= 640 * 480 * 15) {
                maxBitrate = 600;
                minBitrate = 300;
            } else if (pixel_count >= 570 * 432 * 15) {
                maxBitrate = 400;
                minBitrate = 200;
            } else if (pixel_count >= 480 * 360 * 15) {
                maxBitrate = 350;
            } else if (pixel_count >= 320 * 240 * 15) {
                maxBitrate = 200;
            }
        }
        return new int[]{minBitrate, maxBitrate};
    }

    private boolean createOffer() {
        if (peerConnection == null) {
            return false;
        }

        Log.d(TAG, "createOffer");
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(final SessionDescription desc) {

                String sdp = preferCodec(desc.description, "H264", false);
                SessionDescription sdpRemote = new SessionDescription(desc.type, sdp);

                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(final SessionDescription desc) {
                        Log.d(TAG, "desc " + desc.description);
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "createOffer set succesfully\n" + sdp);
                        events.onLocalDescription(desc);
                    }

                    @Override
                    public void onCreateFailure(final String error) {
                    }

                    @Override
                    public void onSetFailure(final String error) {
                        events.onPeerConnectionError("createOffer set error: " + error);
                    }
                }, sdpRemote);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(final String error) {
                events.onPeerConnectionError("createOffer error: " + error);
            }

            @Override
            public void onSetFailure(final String error) {
            }
        }, new MediaConstraints());

        return true;
    }

    private boolean createAnswer() {
        if (peerConnection == null) {
            return false;
        }
        Log.d(TAG, "createAnswer");
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(final SessionDescription desc) {

                String sdp = preferCodec(desc.description, "H264", false);
                SessionDescription sdpRemote = new SessionDescription(desc.type, sdp);
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(final SessionDescription desc) {
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "createAnswer set succesfully");
                        events.onLocalDescription(desc);
                    }

                    @Override
                    public void onCreateFailure(final String error) {
                    }

                    @Override
                    public void onSetFailure(final String error) {
                        events.onPeerConnectionError("createAnswer set error: " + error);
                    }
                }, sdpRemote);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(final String error) {
                events.onPeerConnectionError("createAnswer error: " + error);
            }

            @Override
            public void onSetFailure(final String error) {
            }
        }, new MediaConstraints());

        return true;
    }

    public boolean setRemoteDescription(final SessionDescription desc) {
        if (peerConnection == null) {
            return false;
        }

        Log.d(TAG, "setRemoteDescription.");

        String sdp = preferCodec(desc.description, "H264", false);
        SessionDescription sdpRemote = new SessionDescription(desc.type, sdp);
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(final SessionDescription desc) {

            }

            @Override
            public void onSetSuccess() {
                if (desc.type == SessionDescription.Type.OFFER) {
                    createAnswer();
                }
            }

            @Override
            public void onCreateFailure(final String error) {
            }

            @Override
            public void onSetFailure(final String error) {
                events.onPeerConnectionError("setRemoteDescription error: " + error);
            }
        }, sdpRemote);

        return true;
    }

    public boolean addRemoteIceCandidate(final IceCandidate candidate) {
        if (peerConnection == null) {
            return false;
        }
        peerConnection.addIceCandidate(candidate);

        return true;
    }

    // Implementation detail: observe ICE & stream changes and react accordingly.
    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            events.onIceCandidate(candidate);
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
            events.onIceCandidatesRemoved(candidates);
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
            Log.d(TAG, "SignalingState: " + newState);
        }

        @Override
        public void onIceConnectionChange(final IceConnectionState newState) {
            Log.d(TAG, "IceConnectionState: " + newState);
            if (newState == IceConnectionState.CONNECTED) {
                events.onIceConnected();
            } else if (newState == IceConnectionState.DISCONNECTED) {
                events.onIceDisconnected();
            } else if (newState == IceConnectionState.FAILED) {
                events.onPeerConnectionError("ICE connection failed");
            }
        }

        @Override
        public void onConnectionChange(final PeerConnectionState newState) {
            Log.d(TAG, "PeerConnectionState: " + newState);
            if (newState == PeerConnectionState.CONNECTED) {
                events.onConnected();
            } else if (newState == PeerConnectionState.DISCONNECTED) {
                events.onDisconnected();
            } else if (newState == PeerConnectionState.FAILED) {
                events.onPeerConnectionError("DTLS connection failed");
            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
            Log.d(TAG, "Selected candidate pair changed because: " + event);
        }

        @Override
        public void onAddStream(final MediaStream stream) {
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            Log.d(TAG, "New Data channel " + dc.label());

            if (!dataChannelEnabled)
                return;

            dc.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long previousAmount) {
                    Log.d(TAG, "Data channel buffered amount changed: " + dc.label() + ": " + dc.state());
                }

                @Override
                public void onStateChange() {
                    Log.d(TAG, "Data channel state changed: " + dc.label() + ": " + dc.state());
                }

                @Override
                public void onMessage(final DataChannel.Buffer buffer) {
                    if (buffer.binary) {
                        Log.d(TAG, "Received binary msg over " + dc);
                        return;
                    }
                    ByteBuffer data = buffer.data;
                    final byte[] bytes = new byte[data.capacity()];
                    data.get(bytes);
                    String strData = new String(bytes, Charset.forName("UTF-8"));
                    Log.d(TAG, "Got msg: " + strData + " over " + dc);
                }
            });
        }

        @Override
        public void onRenegotiationNeeded() {
            // signaling/negotiation protocol.
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    }

    private static String preferCodec(String sdp, String codec, boolean isAudio) {
        return sdp;
//        final String[] lines = sdp.split("\r\n");
//        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
//        if (mLineIndex == -1) {
//            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
//            return sdp;
//        }
//        // A list with all the payload types with name `codec`. The payload types are integers in the
//        // range 96-127, but they are stored as strings here.
//        final List<String> codecPayloadTypes = new ArrayList<>();
//        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
//        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
//        for (String line : lines) {
//            Matcher codecMatcher = codecPattern.matcher(line);
//            if (codecMatcher.matches()) {
//                codecPayloadTypes.add(codecMatcher.group(1));
//            }
//        }
//        if (codecPayloadTypes.isEmpty()) {
//            Log.w(TAG, "No payload types with name " + codec);
//            return sdp;
//        }
//
//        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
//        if (newMLine == null) {
//            return sdp;
//        }
//        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
//        lines[mLineIndex] = newMLine;
//        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    private static @Nullable String movePayloadTypesToFront(
            List<String> preferredPayloadTypes, String mLine) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        // Reconstruct the line with `preferredPayloadTypes` moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    /**
     * Returns the line number containing "m=audio|video", or -1 if no such line exists.
     */
    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

    public void dispose() {
        Log.d(TAG, "closing peer connection.");

        statsTimer.cancel();

        if (dataChannel != null) {
            dataChannel.dispose();
            dataChannel = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        peerConnectionParameters = null;

        Log.d(TAG, "closing peer connection done.");
    }
}
