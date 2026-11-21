package com.desk.dashboard.media

import android.service.notification.NotificationListenerService

/**
 * Empty listener. Its only job is to exist: an enabled notification listener is what lets
 * MediaSessionManager.getActiveSessions() return other apps' sessions (Spotify).
 */
class MediaListener : NotificationListenerService()
