package vision.zeus.services;


import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;

import vision.zeus.plugins.ZeusPushNotifications;

public class CustomFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String newToken) {
        super.onNewToken(newToken);
        ZeusPushNotifications.onNewToken(newToken);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        try {
            ZeusPushNotifications.sendRemoteMessage(remoteMessage, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}