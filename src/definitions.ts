import {Plugin, PluginListenerHandle} from "@capacitor/core/dist/esm/definitions";
import {
  NotificationChannel, NotificationChannelList,
  NotificationPermissionResponse, PushNotification, PushNotificationActionPerformed,
  PushNotificationDeliveredList, PushNotificationToken
} from "@capacitor/core/dist/esm/core-plugin-definitions";
import "@capacitor/core";

declare module '@capacitor/core' {
  interface PluginRegistry {
    ZeusPushNotifications: ZeusPushNotificationsPlugin;
  }
}

export interface ZeusPushNotificationsPlugin extends Plugin {
  /**
   * Register the app to receive push notifications.
   * Will trigger registration event with the push token
   * or registrationError if there was some problem.
   * Doesn't prompt the user for notification permissions, use requestPermission() first.
   */
  register(): Promise<void>;
  /**
   * On iOS it prompts the user to allow displaying notifications
   * and return if the permission was granted or not.
   * On Android there is no such prompt, so just return as granted.
   */
  requestPermission(): Promise<NotificationPermissionResponse>;
  /**
   * Returns the notifications that are visible on the notifications screen.
   */
  getDeliveredNotifications(): Promise<PushNotificationDeliveredList>;
  /**
   * Removes the specified notifications from the notifications screen.
   * @param delivered list of delivered notifications.
   */
  removeDeliveredNotifications(delivered: PushNotificationDeliveredList): Promise<void>;
  /**
   * Removes all the notifications from the notifications screen.
   */
  removeAllDeliveredNotifications(): Promise<void>;
  /**
   * On Android O or newer (SDK 26+) creates a notification channel.
   * @param channel to create.
   */
  createChannel(channel: NotificationChannel): Promise<void>;
  /**
   * On Android O or newer (SDK 26+) deletes a notification channel.
   * @param channel to delete.
   */
  deleteChannel(channel: NotificationChannel): Promise<void>;
  /**
   * On Android O or newer (SDK 26+) list the available notification channels.
   */
  listChannels(): Promise<NotificationChannelList>;
  /**
   * Event called when the push notification registration finished without problems.
   * Provides the push notification token.
   * @param eventName registration.
   * @param listenerFunc callback with the push token.
   */
  addListener(eventName: 'registration', listenerFunc: (token: PushNotificationToken) => void): PluginListenerHandle;
  /**
   * Event called when the push notification registration finished with problems.
   * Provides an error with the registration problem.
   * @param eventName registrationError.
   * @param listenerFunc callback with the registration error.
   */
  addListener(eventName: 'registrationError', listenerFunc: (error: any) => void): PluginListenerHandle;
  /**
   * Event called when the device receives a push notification.
   * @param eventName pushNotificationReceived.
   * @param listenerFunc callback with the received notification.
   */
  addListener(eventName: 'pushNotificationReceived', listenerFunc: (notification: PushNotification) => void): PluginListenerHandle;
  /**
   * Event called when an action is performed on a pusn notification.
   * @param eventName pushNotificationActionPerformed.
   * @param listenerFunc callback with the notification action.
   */
  addListener(eventName: 'pushNotificationActionPerformed', listenerFunc: (notification: PushNotificationActionPerformed) => void): PluginListenerHandle;
  /**
   * Remove all native listeners for this plugin.
   */
  removeAllListeners(): void;
}