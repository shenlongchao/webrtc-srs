package com.webrtc.srs.iinterface

import org.webrtc.FrameDecryptor
import org.webrtc.FrameEncryptor

/**
 * @author gyn
 * @date 2023/6/20
 */
interface IEncryptionCallback {

    fun encrypt(): FrameEncryptor

    fun decrypt(): FrameDecryptor
}