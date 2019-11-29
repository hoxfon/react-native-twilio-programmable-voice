package com.hoxfon.react.RNTwilioVoice.network;

import android.util.Log;
import java.io.IOException;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import io.reactivex.Observable;

import com.hoxfon.react.RNTwilioVoice.models.Visitor;

public class VisitorClient {

  private VisitorApi api;

  public static String TAG = "RNTwilioVoice";

  public VisitorClient(String baseUrl) {

    baseUrl += "/mailman/";

    Log.d(TAG, String.format("VisitorClient Created: [%s]", baseUrl));
    Retrofit retrofit = new Retrofit.Builder()
      .baseUrl(baseUrl)
      .addConverterFactory(GsonConverterFactory.create())
      .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
      .build();
 
    api = retrofit.create(VisitorApi.class);
  }

  public Observable<Visitor> getVisitorInfo(
      final String token, final String callSid) {

    Log.d(TAG, String.format("VisitorClient.getVisitorInfo called with token [%s] and call ID [%s]", token, callSid));
    return api.getVisitorProfile(token, callSid);
  }
}