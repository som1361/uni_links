package name.avioli.unilinks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

//import android.content.IntentFilter;
//import android.util.Log;

/** UniLinksPlugin */
public class UniLinksPlugin
    implements MethodCallHandler, StreamHandler, PluginRegistry.NewIntentListener, InstallReferrerStateListener {
  private static final String MESSAGES_CHANNEL = "uni_links/messages";
  private static final String EVENTS_CHANNEL = "uni_links/events";

  private BroadcastReceiver changeReceiver;
  private Registrar registrar;

  private String initialLink;
  private String latestLink;
  private String referrer;

  private static InstallReferrerClient mReferrerClient;

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {

    // Detect if we've been launched in background
    if (registrar.activity() == null) {
      return;
    }

    UniLinksPlugin instance = new UniLinksPlugin(registrar);

    final MethodChannel mChannel = new MethodChannel(registrar.messenger(), MESSAGES_CHANNEL);
    mChannel.setMethodCallHandler(instance);

    final EventChannel eChannel = new EventChannel(registrar.messenger(), EVENTS_CHANNEL);
    eChannel.setStreamHandler(instance);

    registrar.addNewIntentListener(instance);

    //todo: detect first app launch to get referrer
    mReferrerClient = InstallReferrerClient.newBuilder(registrar.activity()).build();
    mReferrerClient.startConnection(registrar.activity());
  }

  private UniLinksPlugin(Registrar registrar) {
    this.registrar = registrar;
    handleIntent(registrar.context(), registrar.activity().getIntent(), true);
  }

  private void handleIntent(Context context, Intent intent, Boolean initial) {
    String action = intent.getAction();
    String dataString = intent.getDataString();

    if (Intent.ACTION_VIEW.equals(action)) {
      if (initial) initialLink = dataString;
      latestLink = dataString;
      if (changeReceiver != null) changeReceiver.onReceive(context, intent);
    }
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("getInitialLink")) {
      final List<String> linkData = new ArrayList<>();
      linkDatas.add(initialLink);
      linkData.add(referrer);
      result.success(linkData);
      // } else if (call.method.equals("getLatestLink")) {
      //   result.success(latestLink);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onListen(Object arguments, EventSink events) {
    changeReceiver = createChangeReceiver(events);
    // registrar.activity().registerReceiver(
    // changeReceiver, new IntentFilter(Intent.ACTION_VIEW));
  }

  @Override
  public void onCancel(Object arguments) {
    // registrar.activity().unregisterReceiver(changeReceiver);
    changeReceiver = null;
  }

  @Override
  public boolean onNewIntent(Intent intent) {
    handleIntent(registrar.context(), intent, false);
    return false;
  }

  @Override
  public void onInstallReferrerSetupFinished(int responseCode) {
    switch (responseCode) {
    case InstallReferrerClient.InstallReferrerResponse.OK:
      // Connection established
      try {
        //get referrer data
        ReferrerDetails response = mReferrerClient.getInstallReferrer();
        referrer = response.getInstallReferrer();
        mReferrerClient.endConnection();
      } catch (RemoteException e) {
        e.printStackTrace();
      }

      break;
    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
      // API not available on the current Play Store app
      break;
    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
      // Connection could not be established
      break;
    case InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR:
      break;
    case InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED:
      break;
    }
  }

  @Override
  public void onInstallReferrerServiceDisconnected() {

  }

  private BroadcastReceiver createChangeReceiver(final EventSink events) {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        // NOTE: assuming intent.getAction() is Intent.ACTION_VIEW

        // Log.v("uni_links", String.format("received action: %s", intent.getAction()));

        String dataString = intent.getDataString();

        if (dataString == null) {
          events.error("UNAVAILABLE", "Link unavailable", null);
        } else {
          events.success(dataString);
        }
      }
    };
  }
}
