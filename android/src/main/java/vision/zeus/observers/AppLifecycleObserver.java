package vision.zeus.observers;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.getcapacitor.Logger;

import vision.zeus.plugins.ZeusPushNotifications;

public class AppLifecycleObserver implements LifecycleObserver {

    public static final String TAG = AppLifecycleObserver.class.getName();
    public static boolean inForeground = false;


    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onEnterForeground() {
        Logger.debug("App on foreground");
        AppLifecycleObserver.inForeground = true;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onEnterBackground() {
        Logger.debug("App on background");
        AppLifecycleObserver.inForeground = false;
    }

}
