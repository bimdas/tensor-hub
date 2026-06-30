package com.taoleeeee.tensorhub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.taoleeeee.tensorhub.server.InferenceService

/**
 * Auto-starts the inference server on device boot.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, starting Tensor Hub server")
            val serviceIntent = Intent(context, InferenceService::class.java).apply {
                action = InferenceService.ACTION_START
                putExtra(InferenceService.EXTRA_PORT, InferenceService.DEFAULT_PORT)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
