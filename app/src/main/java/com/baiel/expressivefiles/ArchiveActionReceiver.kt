package com.baiel.expressivefiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.baiel.expressivefiles.viewmodel.FileViewModel

/**
 * Handles the actions of the notification shown while an archive operation's
 * progress popup is hidden:
 *   - Cancel: interrupts the running coroutine through the ViewModel's static
 *     job handle; the unwind handler in the coroutine publishes the
 *     cancellation toast and cleans up all state.
 *   - Show dialog is an activity PendingIntent (EXTRA_SHOW_ARCHIVE_DIALOG) and
 *     does not go through this receiver.
 */
class ArchiveActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL_ARCHIVE) {
            FileViewModel.cancelActiveArchive()
        }
    }

    companion object {
        const val ACTION_CANCEL_ARCHIVE = "com.baiel.expressivefiles.action.CANCEL_ARCHIVE"
        const val EXTRA_SHOW_ARCHIVE_DIALOG = "com.baiel.expressivefiles.extra.SHOW_ARCHIVE_DIALOG"
    }
}
