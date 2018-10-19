package com.hoxfon.react.RNTwilioVoice.network;

import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import io.reactivex.Observable;
import com.hoxfon.react.RNTwilioVoice.models.Visitor;

public interface VisitorApi {

  @Headers("content-type: application/json")
  @GET("call/{callId}")
  Observable<Visitor> getVisitorProfile(
    @Header("X-Authorization") final String token,
    @Path("callId") final String callId);

}