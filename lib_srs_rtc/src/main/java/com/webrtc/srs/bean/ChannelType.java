package com.webrtc.srs.bean;

/**
 * Created by shen on 2023/7/28
 */
public enum ChannelType {

    PUBLISH("publish"),//推流到远端
    SUBSCRIBE("subscribe");//拉流到本地

    ChannelType(String type) {

    }

    public ChannelType ParseActionType(String type){
        if(type.equals("push")) {
            return PUBLISH;
        } else {
            return SUBSCRIBE;
        }
    }

}
