package com.taoleeeee.tensorhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.taoleeeee.tensorhub.ui.screens.HomeScreen
import com.taoleeeee.tensorhub.ui.screens.ModelScreen
import com.taoleeeee.tensorhub.ui.screens.SettingsScreen
import com.taoleeeee.tensorhub.ui.theme.TensorHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TensorHubTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        Triple("Home", Icons.Default.Home, 0),
        Triple("Models", Icons.Default.ModelTraining, 1),
        Triple("Settings", Icons.Default.Settings, 2),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Tensor Hub") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> ModelScreen()
                2 -> SettingsScreen()
            }
        }
    }
}
