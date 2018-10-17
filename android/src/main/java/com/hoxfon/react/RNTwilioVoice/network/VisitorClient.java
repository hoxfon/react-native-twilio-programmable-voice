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

  public VisitorClient() {
    Retrofit retrofit = new Retrofit.Builder()
      .baseUrl("http://api.qa.keenvil.com:8080/guard/c/santacatalina/")
      .addConverterFactory(GsonConverterFactory.create())
      .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
      .build();
 
    api = retrofit.create(VisitorApi.class);
  }

  public Observable<Visitor> getVisitorInfo(
      final String token, final String callSid) {

    Log.d(TAG, "VisitorCLient.getVisitorInfo called");
    return api.getVisitorProfile(token, callSid);
  }
}