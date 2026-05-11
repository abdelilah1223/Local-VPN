package com.elbaroudi.localvpn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import com.elbaroudi.localvpn.ui.BlockListScreen
import com.elbaroudi.localvpn.ui.HomeScreen
import com.elbaroudi.localvpn.ui.VpnViewModel
import com.elbaroudi.localvpn.ui.theme.LocalvpnTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val startDestination = if (intent?.extras?.getString("route") == "blocklist") "blocklist" else "home"
        
        setContent {
            LocalvpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: VpnViewModel = viewModel() // Scoped to Activity
                    
                    // Handle Shortcuts
                    LaunchedEffect(intent) {
                        when(intent?.action) {
                            "com.elbaroudi.localvpn.ACTION_START" -> {
                                viewModel.startVpn()
                                // Possibly move task to back if we want silent start?
                            }
                            "com.elbaroudi.localvpn.ACTION_STOP" -> {
                                viewModel.stopVpn()
                            }
                        }
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToBlockList = {
                                    navController.navigate("blocklist")
                                }
                            )
                        }
                        composable("blocklist") {
                            BlockListScreen(
                                viewModel = viewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}