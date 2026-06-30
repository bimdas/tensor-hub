package com.taoleeeee.tensorhub.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.taoleeeee.tensorhub.MainActivity
import com.taoleeeee.tensorhub.R
import com.taoleeeee.tensorhub.delegate.DelegateManager
import com.taoleeeee.tensorhub.inference.InferenceEngine
import com.taoleeeee.tensorhub.model.ModelManager

/**
 * Foreground service that keeps the HTTP server alive when the app is backgrounded.
 *
 * Uses NotificationCompat + startForeground() to prevent Android from killing the process.
 * Binds strictly to 127.0.0.1 (localhost only) so the inference API is never exposed
 * to the wider network.
 */
class InferenceService : Service() {

    companion object {
        private const val TAG = "InferenceService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "tensor_hub_server"
        const val ACTION_START = "com.taoleeeee.tensorhub.START"
        const val ACTION_STOP = "com.taoleeeee.tensorhub.STOP"
        const val EXTRA_PORT = "port"

        // Hardcoded localhost - never expose to network
        const val BIND_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8190
    }

    private var httpServer: TensorHubHttpServer? = null
    private var modelManager: ModelManager? = null
    private var inferenceEngine: InferenceEngine? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                startServer(port)
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
            else -> {
                // Default: start server on default port
                startServer(DEFAULT_PORT)
            }
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        if (httpServer?.isAlive == true) {
            Log.i(TAG, "Server already running on port $port")
            return
        }

        // Start foreground FIRST - Android requires this within 5 seconds of startForegroundService()
        val notification = buildNotification(port)
        startForeground(NOTIFICATION_ID, notification)

        // Initialize ML stack
        modelManager = ModelManager(applicationContext)
        val delegateManager = DelegateManager(applicationContext)
        inferenceEngine = InferenceEngine(modelManager!!, delegateManager)

        // Start HTTP server bound to localhost only
        httpServer = TensorHubHttpServer(
            host = BIND_HOST,
            port = port,
            inferenceEngine = inferenceEngine!!,
            modelManager = modelManager!!
        ).also {
            it.start()
            Log.i(TAG, "Server started on http://$BIND_HOST:$port")
        }
    }

    private fun stopServer() {
        httpServer?.stop()
        httpServer = null
        inferenceEngine?.close()
        inferenceEngine = null
        modelManager = null
        Log.i(TAG, "Server stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tensor Hub Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the inference server running in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(port: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, InferenceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tensor Hub")
            .setContentText("Inference server running on $BIND_HOST:$port")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
