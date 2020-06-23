package com.hoxfon.react.RNTwilioVoice.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.net.Uri;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.cardview.widget.CardView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ReactContext;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import com.hoxfon.react.RNTwilioVoice.R;
import com.hoxfon.react.RNTwilioVoice.network.VisitorClient;
import com.hoxfon.react.RNTwilioVoice.models.Visitor;
import java.io.InputStream;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_ALLOW_VISITOR;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_CANCEL_CALL_INVITE;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_DISCONNECTED_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_HANGUP_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_REJECT_VISITOR;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_REQUEST_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_SPEAKER_OFF;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_SPEAKER_ON;


/**
 * Created by estebanabait on 6/7/18.
 */

public class AutomaticCallScreenActivity extends ReactActivity {
  public static String TAG = "RNTwilioVoice.AutomaticCallScreen";
  private AutomaticCallScreenActivity.AutomaticCallBroadcastReceiver automaticCallBroadcastReceiver;
  private boolean isReceiverRegistered = false;
  private boolean isSpeakerOn = false;

  private VisitorClient visitorService;

  /**
   * Collects all subscriptions to unsubscribe later
   */
  private CompositeDisposable compositeDisposable = new CompositeDisposable();

  private CardView visitorProfile;
  private int shortAnimationDuration;
  private String s3Url;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().addFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
    );

    setContentView(R.layout.activity_automatic_call);

    final ReactContext reactContext = getReactInstanceManager().getCurrentReactContext();

    SharedPreferences sharedPref = reactContext.getSharedPreferences("com.hoxfon.react.RNTwilioVoice.config", MODE_PRIVATE);
    String baseUrl = sharedPref.getString("BASE_URL", "");
    s3Url = sharedPref.getString("S3_URL", "");

    visitorService = new VisitorClient(baseUrl);

    automaticCallBroadcastReceiver = new AutomaticCallScreenActivity.AutomaticCallBroadcastReceiver();
    registerReceiver();

    String callSid = getIntent().getStringExtra("CALL_SID");
    String token = getIntent().getStringExtra("SESSION_TOKEN");
    String community = getIntent().getStringExtra("ACTIVE_COMMUNITY");

    requestVisitorProfile(token, callSid);

    visitorProfile = (CardView) findViewById(R.id.visitor_profile);

    // set invisible when started. We'll animate it when the data is ready.
    visitorProfile.setVisibility(View.GONE);

    //Retrieve default system animation duration.
    shortAnimationDuration = getResources().getInteger(
      android.R.integer.config_shortAnimTime);

    Button speakerBtn = (Button) findViewById(R.id.speaker_btn);
    speakerBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        String action;
        toggleSpeaker();
        if (isSpeakerOn()) {
          action = ACTION_SPEAKER_ON;
        } else {
          action = ACTION_SPEAKER_OFF;
        }
        updateSpeakerButton();
        Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
      }
    });

    Button allowVisitorBtn = (Button) findViewById(R.id.allow_visitor_btn);
    allowVisitorBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_ALLOW_VISITOR);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
        finish();
      }
    });

    Button rejectVisitorBtn = (Button) findViewById(R.id.reject_visitor_btn);
    rejectVisitorBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_REJECT_VISITOR);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
        finish();
      }
    });

    Button callRequestBtn = (Button) findViewById(R.id.request_call_btn);
    callRequestBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_REQUEST_CALL);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
        finish();
      }
    });
  }

  @Override protected void onPause() {
    super.onPause();
    if (isReceiverRegistered) {
      LocalBroadcastManager.getInstance(
          getReactInstanceManager().getCurrentReactContext()
      ).unregisterReceiver(automaticCallBroadcastReceiver);
    }
  }

  private class AutomaticCallBroadcastReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {

      String action = intent.getAction();
      Log.d(TAG, "ACTION RECEIVED " + action);
      if (action.equals(ACTION_CANCEL_CALL_INVITE) || action.equals(ACTION_DISCONNECTED_CALL)) {
        finish();
      }
    }
  }

  private void requestVisitorProfile(final String token,
      final String callSid) {

    compositeDisposable.add(visitorService.getVisitorInfo(token, callSid)
      .subscribeOn(Schedulers.io()) // "work" on io thread
      .observeOn(AndroidSchedulers.mainThread()) // "listen" on UIThread
      .subscribeWith(new DisposableObserver<Visitor>() {
        @Override
        public void onNext(final Visitor visitor) {
            Log.d(TAG, "Accept Visitor Information");
            ImageView visitorAvatar = (ImageView) findViewById(R.id.visitor_avatar);
            ImageDownloader imageDownLoader = new ImageDownloader(visitorAvatar);
            imageDownLoader.execute(String.format("%s/%s", s3Url, visitor.getVisitorAvatarUri()));
            displayVisitorCard(visitor);
        }

        @Override
        public void onError(Throwable e) {
          Log.e(TAG, "Error on request", e);
        }

        @Override
        public void onComplete() {

        }
      })
    );
  }

  private void displayVisitorCard(final Visitor visitor) {

    Log.d(TAG, "displayVisitorCard");
    TextView visitorName = (TextView) findViewById(R.id.visitor_name);
    visitorName.setText(visitor.getVisitorName());

    Log.d(TAG, String.format("displayVisitorCard Name Added: [%s]", visitor.getVisitorName()));

    String type = visitor.getProviderType() != null ? visitor.getProviderType()
    : getRelationshipName(visitor.getRelationship());
    TextView visitorType = (TextView) findViewById(R.id.visitor_type);
    visitorType.setText(type);

    Log.d(TAG, String.format("displayVisitorCard Type Added: [%s]", type));

    Uri uri = Uri.parse(
      String.format("%s/%s", s3Url, visitor.getVisitorAvatarUri())
    );
    ImageView visitorAvatar = (ImageView) findViewById(R.id.visitor_avatar);
    visitorAvatar.setImageURI(uri);

    Log.d(TAG, String.format("displayVisitorCard Avatar Added: [%s]", uri));
  }

  private class ImageDownloader extends AsyncTask<String, Void, Bitmap> {
    ImageView bmImage;

    public ImageDownloader(ImageView bmImage) {
        this.bmImage = bmImage;
    }

    protected Bitmap doInBackground(String... urls) {
        String url = urls[0];
        Bitmap bitmap = null;
        try {
            InputStream in = new java.net.URL(url).openStream();
            bitmap = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("MyApp", e.getMessage());
        }
        return bitmap;
    }

    protected void onPostExecute(Bitmap result) {
        bmImage.setImageBitmap(result);

    visitorProfile.setAlpha(0f);
    visitorProfile.setVisibility(View.VISIBLE);

    //Fade the Visitor Profile Card in.
    visitorProfile.animate()
      .alpha(1f)
      .setDuration(shortAnimationDuration)
      .setListener(null);
    }
}

  private String getRelationshipName(String relationship) {
    switch(relationship) {
      case "FAMILY" :   return "Familiar / Amigo";
      case "DELIVERY":  return "Delivery";
      case "SERVICE":   return "Service";
      case "EMPLOYEE":  return "Empleado";
    }
    return "";
  }

  /**
   * Register the Voice broadcast receiver
   */
  private void registerReceiver() {

    if (!isReceiverRegistered) {
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction(ACTION_CANCEL_CALL_INVITE);
      intentFilter.addAction(ACTION_DISCONNECTED_CALL);

      LocalBroadcastManager.getInstance(getReactInstanceManager().getCurrentReactContext())
          .registerReceiver(automaticCallBroadcastReceiver, intentFilter);

      isReceiverRegistered = true;
    }
  }

  private void toggleSpeaker() {
    this.isSpeakerOn = !this.isSpeakerOn;
  }

  private boolean isSpeakerOn() {
    return this.isSpeakerOn;
  }

  private void updateSpeakerButton() {
    Button speakerBtn = (Button) findViewById(R.id.speaker_btn);
    int resourceId;
    if (isSpeakerOn()) {
      resourceId = R.drawable.speaker_on;
    } else {
      resourceId = R.drawable.speaker_off;
    }
    Drawable top = getResources().getDrawable(resourceId);
    speakerBtn.setCompoundDrawablesWithIntrinsicBounds(
        null,
        top,
        null,
        null
    );
  }

  @Override
  protected void onDestroy() {
    compositeDisposable.clear();
    super.onDestroy();
  }
}
