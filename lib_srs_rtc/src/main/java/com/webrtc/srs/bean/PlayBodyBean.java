package com.webrtc.srs.bean;

public class PlayBodyBean {

    private String api;
    private String tid;
    private String streamurl;
    private String clientip;
    private String sdp;

    public void setApi(String api) {
        this.api = api;
    }

    public String getApi() {
        return api;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getTid() {
        return tid;
    }

    public void setStreamurl(String streamurl) {
        this.streamurl = streamurl;
    }

    public String getStreamurl() {
        return streamurl;
    }

    public void setClientip(String clientip) {
        this.clientip = clientip;
    }

    public String getClientip() {
        return clientip;
    }

    public void setSdp(String sdp) {
        this.sdp = sdp;
    }

    public String getSdp() {
        return sdp;
    }

    @Override
    public String toString() {
        return "PlayBodyBean{" +
                "api='" + api + '\'' +
                ", tid='" + tid + '\'' +
                ", streamurl='" + streamurl + '\'' +
                ", clientip='" + clientip + '\'' +
                ", sdp='" + '\'' +
                '}';
    }
}
