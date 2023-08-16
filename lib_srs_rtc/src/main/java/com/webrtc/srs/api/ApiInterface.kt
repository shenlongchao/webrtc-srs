package com.webrtc.srs.api

import com.webrtc.srs.bean.PlayBodyBean
import com.webrtc.srs.bean.SdpBean
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Created by shen on 2023/7/31
 */
interface ApiInterface {

    @POST
    @Headers("Content-Type: application/json")//,"Accept: application/json"
    fun webrtcPlay(@Url url: String?, @Body gsonStr: PlayBodyBean?): Call<SdpBean>

}