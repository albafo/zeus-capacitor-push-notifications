package vision.zeus.plugins;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginHandle;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.android.R;
import com.getcapacitor.plugin.notification.NotificationChannelManager;
import com.getcapacitor.plugin.util.AssetUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import vision.zeus.observers.AppLifecycleObserver;

@NativePlugin()
public class ZeusPushNotifications extends Plugin {

  public static Bridge staticBridge = null;
  public static RemoteMessage lastMessage = null;
  public NotificationManager notificationManager;
  private NotificationChannelManager notificationChannelManager;

  private static final String EVENT_TOKEN_CHANGE = "registration";
  private static final String EVENT_TOKEN_ERROR = "registrationError";
  private static final String CHANNEL_ID = "default-channel-id";
  private static final String CHANNEL_NAME = "Default channel";
  private static final String CHANNEL_DESCRIPTION = "App Default channel";



  public void load() {
    Logger.debug("Loading ZeusPushNotifications");

    AppLifecycleObserver appLifecycleObserver = new AppLifecycleObserver();
    ProcessLifecycleOwner.get().getLifecycle().addObserver(appLifecycleObserver);

    notificationManager = (NotificationManager)getActivity()
            .getSystemService(Context.NOTIFICATION_SERVICE);
    staticBridge = this.bridge;
    if (lastMessage != null) {
      fireNotification(lastMessage);
      lastMessage = null;
    }
    notificationChannelManager = new NotificationChannelManager(getActivity(), notificationManager);
    createDefaultNotificationChannel();
  }

  private void createDefaultNotificationChannel() {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      int importance = NotificationManager.IMPORTANCE_DEFAULT;
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
      channel.setDescription(CHANNEL_DESCRIPTION);
      // Register the channel with the system; you can't change the importance
      // or other notification behaviors after this
      notificationManager.createNotificationChannel(channel);
    }
  }

  @Override
  protected void handleOnNewIntent(Intent data) {
    super.handleOnNewIntent(data);
    Bundle bundle = data.getExtras();
    if(bundle == null) {
      Logger.debug("Push notification: Bundle is null!!");

    }
    if(data.hasExtra("google.message_id")) {
      Logger.debug("Performed push notification:"+bundle.containsKey("google.message_id"));
    }
    if (bundle != null && bundle.containsKey("google.message_id")) {

      JSObject notificationJson = new JSObject();
      JSObject dataObject = new JSObject();
      for (String key : bundle.keySet()) {
        if (key.equals("google.message_id")) {
          notificationJson.put("id", bundle.get(key));
        } else {
          Object value = bundle.get(key);
          String valueStr = (value != null) ? value.toString() : null;
          dataObject.put(key, valueStr);
        }
      }
      notificationJson.put("data", dataObject);
      JSObject actionJson = new JSObject();
      actionJson.put("actionId", "tap");
      actionJson.put("notification", notificationJson);
      notifyListeners("pushNotificationActionPerformed", actionJson, true);
    }
  }

  @PluginMethod()
  public void register(PluginCall call) {
    FirebaseMessaging.getInstance().setAutoInitEnabled(true);
    FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(getActivity(), new OnSuccessListener<InstanceIdResult>() {
      @Override
      public void onSuccess(InstanceIdResult instanceIdResult) {
        sendToken(instanceIdResult.getToken());
      }
    });
    FirebaseInstanceId.getInstance().getInstanceId().addOnFailureListener(new OnFailureListener() {
      public void onFailure(Exception e) {
        sendError(e.getLocalizedMessage());
      }
    });
    call.success();
  }

  @PluginMethod()
  public void requestPermission(PluginCall call) {
    JSObject result = new JSObject();
    result.put("granted", true);
    call.success(result);
  }

  @PluginMethod()
  public void getDeliveredNotifications(PluginCall call) {
    JSArray notifications = new JSArray();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();

      for (StatusBarNotification notif : activeNotifications) {
        JSObject jsNotif = new JSObject();

        jsNotif.put("id", notif.getId());

        Notification notification = notif.getNotification();
        if (notification != null) {
          jsNotif.put("title", notification.extras.getCharSequence(Notification.EXTRA_TITLE));
          jsNotif.put("body", notification.extras.getCharSequence(Notification.EXTRA_TEXT));
          jsNotif.put("group", notification.getGroup());
          jsNotif.put("groupSummary", 0 != (notification.flags & Notification.FLAG_GROUP_SUMMARY));

          JSObject extras = new JSObject();

          for (String key : notification.extras.keySet()) {
            extras.put(key, notification.extras.get(key));
          }

          jsNotif.put("data", extras);
        }

        notifications.put(jsNotif);
      }
    }

    JSObject result = new JSObject();
    result.put("notifications", notifications);
    call.resolve(result);
  }

  @PluginMethod()
  public void removeDeliveredNotifications(PluginCall call) {
    JSArray notifications = call.getArray("notifications");

    List<Integer> ids = new ArrayList<>();
    try {
      for (Object o : notifications.toList()) {
        if (o instanceof JSONObject) {
          JSObject notif = JSObject.fromJSONObject((JSONObject) o);
          Integer id = notif.getInteger("id");
          ids.add(id);
        } else {
          call.reject("Expected notifications to be a list of notification objects");
        }
      }
    } catch (JSONException e) {
      call.reject(e.getMessage());
    }

    for (int id : ids) {
      notificationManager.cancel(id);
    }

    call.resolve();
  }

  @PluginMethod()
  public void removeAllDeliveredNotifications(PluginCall call) {
    notificationManager.cancelAll();
    call.success();
  }

  @PluginMethod()
  public void createChannel(PluginCall call) {
    notificationChannelManager.createChannel(call);
  }

  @PluginMethod()
  public void deleteChannel(PluginCall call) {
    notificationChannelManager.deleteChannel(call);
  }

  @PluginMethod()
  public void listChannels(PluginCall call) {
    notificationChannelManager.listChannels(call);
  }

  public void sendToken(String token) {
    JSObject data = new JSObject();
    data.put("value", token);
    notifyListeners(EVENT_TOKEN_CHANGE, data, true);
  }

  public void sendError(String error) {
    JSObject data = new JSObject();
    data.put("error", error);
    notifyListeners(EVENT_TOKEN_ERROR, data, true);
  }

  public static void onNewToken(String newToken) {
    ZeusPushNotifications pushPlugin = ZeusPushNotifications.getPushNotificationsInstance();
    if (pushPlugin != null) {
      pushPlugin.sendToken(newToken);
    }
  }

  public static void sendRemoteMessage(RemoteMessage remoteMessage, Context context) throws IOException {
    /*PushNotifications pushPlugin = PushNotifications.getPushNotificationsInstance();
    if (pushPlugin != null) {
      pushPlugin.fireNotification(remoteMessage);
    } else {*/
      Logger.debug("Push data received on background");
      Logger.debug("Push data: contains title: "+remoteMessage.getData().containsKey("title"));
      Logger.debug("Push data: contains notification_id: "+remoteMessage.getData().containsKey("notification_id"));
      Logger.debug("Push data: contains smallIcon: "+remoteMessage.getData().containsKey("smallIcon"));

      if(remoteMessage.getData().containsKey("title") && remoteMessage.getData().containsKey("notification_id")) {
        sendLocalNotification(remoteMessage, context);
      }
      else lastMessage = remoteMessage;
    //}
  }

  private static void sendLocalNotification(RemoteMessage remoteMessage, Context context) throws IOException {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(remoteMessage.getData().get("title"));

    if(remoteMessage.getData().containsKey("body")) {
      builder = builder.setContentText(remoteMessage.getData().get("body"));
      builder.setStyle(new NotificationCompat.BigTextStyle().bigText(remoteMessage.getData().get("body")));
    }
    builder = builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);

    int resId = AssetUtil.RESOURCE_ID_ZERO_VALUE;

    if(remoteMessage.getData().containsKey("smallIcon")) {
      resId = AssetUtil.getResourceID(context, remoteMessage.getData().get("smallIcon"),"drawable");
    }

    if(resId == AssetUtil.RESOURCE_ID_ZERO_VALUE){
      Logger.debug("Push data: using default small icon");
      resId = android.R.drawable.ic_dialog_info;
    }
    builder.setSmallIcon(resId);

    if(remoteMessage.getData().containsKey("largeIcon")) {
      URL url = new URL(remoteMessage.getData().get("largeIcon"));
      Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
      builder.setLargeIcon(bmp);
    }

    ComponentName myService = new ComponentName(context, context.getClass());
    try {
      Bundle data = context.getPackageManager().getServiceInfo(myService, PackageManager.GET_META_DATA).metaData;
      Logger.debug("Intent with mainclass: "+data.getString("mainClass"));

      Intent intent = new Intent(data.getString("mainClass"));
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

      intent.putExtra("google.message_id", remoteMessage.getData().get("notification_id"));
      for (String key : remoteMessage.getData().keySet()) {
        Object value = remoteMessage.getData().get(key);
        intent.putExtra(key, value != null ? value.toString() : null);
      }
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);


      builder.setContentIntent(pendingIntent);



      NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

      boolean isActive = false;
      ZeusPushNotifications pushPlugin = ZeusPushNotifications.getPushNotificationsInstance();

      isActive = pushPlugin != null && AppLifecycleObserver.inForeground;

      Logger.debug("App isActive?: "+isActive);


      if(!isActive || (remoteMessage.getData().containsKey("foreground") &&
              remoteMessage.getData().get("foreground").equals("true"))) {
        // notificationId is a unique int for each notification that you must define
        Logger.debug("Send local notification id: "+Integer.parseInt(remoteMessage.getData().get("notification_id")));
        notificationManager.notify(Integer.parseInt(remoteMessage.getData().get("notification_id")), builder.build());
      }
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }






  }

  public void fireNotification(RemoteMessage remoteMessage) {
    JSObject remoteMessageData = new JSObject();

    JSObject data = new JSObject();
    remoteMessageData.put("id", remoteMessage.getMessageId());
    for (String key : remoteMessage.getData().keySet()) {
      Object value = remoteMessage.getData().get(key);
      data.put(key, value);
    }
    remoteMessageData.put("data", data);

    RemoteMessage.Notification notification = remoteMessage.getNotification();
    if (notification != null) {
      remoteMessageData.put("title", notification.getTitle());
      remoteMessageData.put("body", notification.getBody());
      remoteMessageData.put("click_action", notification.getClickAction());

      Uri link = notification.getLink();
      if (link != null) {
        remoteMessageData.put("link", link.toString());
      }
    }

    notifyListeners("pushNotificationReceived", remoteMessageData, true);
  }

  public static ZeusPushNotifications getPushNotificationsInstance() {
    if (staticBridge != null && staticBridge.getWebView() != null) {
      PluginHandle handle = staticBridge.getPlugin("ZeusPushNotifications");
      if (handle == null) {
        return null;
      }
      return (ZeusPushNotifications) handle.getInstance();
    }
    return null;
  }

}
