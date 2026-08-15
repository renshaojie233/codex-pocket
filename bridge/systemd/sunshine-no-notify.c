/*
 * Sunshine 0.22.x always creates its Linux system tray and has no supported
 * switch for disabling the stream start/pause desktop notifications.
 *
 * Load this tiny compatibility shim with LD_PRELOAD for the Sunshine process.
 * It keeps the tray and streaming code intact, but turns libnotify's final
 * display call into a successful no-op.  The notification object is still
 * created and released normally, so callers retain their expected lifecycle.
 */
typedef int gboolean;
typedef struct _NotifyNotification NotifyNotification;
typedef struct _GError GError;

__attribute__((visibility("default"))) gboolean
notify_notification_show(NotifyNotification *notification, GError **error) {
  (void) notification;
  (void) error;
  return 1;
}
