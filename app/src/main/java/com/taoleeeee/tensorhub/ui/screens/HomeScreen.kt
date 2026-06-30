package com.taoleeeee.tensorhub.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.taoleeeee.tensorhub.server.InferenceService

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var serverRunning by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf("8190") }

    // Auto-start server on first launch
    LaunchedEffect(Unit) {
        val intent = Intent(context, InferenceService::class.java)
        intent.action = InferenceService.ACTION_START
        intent.putExtra(InferenceService.EXTRA_PORT, port.toIntOrNull() ?: 8190)
        context.startForegroundService(intent)
        serverRunning = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Server status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (serverRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (serverRunning) "Server Running" else "Server Stopped",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (serverRunning) "http://127.0.0.1:$port" else "Tap Start to begin",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Start/Stop button
        Button(
            onClick = {
                val intent = Intent(context, InferenceService::class.java)
                if (serverRunning) {
                    intent.action = InferenceService.ACTION_STOP
                    context.stopService(intent)
                } else {
                    intent.action = InferenceService.ACTION_START
                    intent.putExtra(InferenceService.EXTRA_PORT, port.toIntOrNull() ?: 8190)
                    context.startForegroundService(intent)
                }
                serverRunning = !serverRunning
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serverRunning)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (serverRunning) "Stop Server" else "Start Server")
        }

        // Info cards
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connection", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Host: 127.0.0.1 (localhost only)")
                Text("Port: $port")
                Text("Accessible from: Termux, ADB forward")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How to use from Termux", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        appendLine("# Health check")
                        appendLine("curl http://127.0.0.1:$port/health")
                        appendLine()
                        appendLine("# Transcribe audio")
                        appendLine("curl -X POST http://127.0.0.1:$port/v1/audio/transcriptions \\")
                        appendLine("  -F file=@audio.wav -F model=whisper-base")
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
