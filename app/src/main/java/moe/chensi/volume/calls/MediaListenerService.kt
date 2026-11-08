package moe.chensi.volume.calls

import android.service.notification.NotificationListenerService

/**
 * Empty listener. Being an enabled notification listener is what allows
 * MediaSessionManager.getActiveSessions(), i.e. pausing other apps' media.
 */
class MediaListenerService : NotificationListenerService()
