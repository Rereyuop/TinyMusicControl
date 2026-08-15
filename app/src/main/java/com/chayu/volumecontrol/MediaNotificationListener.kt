package com.chayu.volumecontrol

import android.service.notification.NotificationListenerService

/**
 * Enables this app to receive the user's explicit notification-listener grant.
 * The active media sessions themselves are read by MainActivity.
 */
class MediaNotificationListener : NotificationListenerService()
