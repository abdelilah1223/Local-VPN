package com.elbaroudi.localvpn.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elbaroudi.localvpn.AdBlockVpnService
import com.elbaroudi.localvpn.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VpnViewModel,
    onNavigateToBlockList: () -> Unit
) {
    val context = LocalContext.current
    var isVpnActive by remember { mutableStateOf(false) } // TODO: Observe read service state
    
    // Check if current language is Arabic
    val currentLocale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
    val isArabic = currentLocale.language == "ar"

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn()
            isVpnActive = true
        }
    }
    
    // Function to toggle language
    fun toggleLanguage() {
        val newLocale = if (isArabic) LocaleListCompat.forLanguageTags("en") else LocaleListCompat.forLanguageTags("ar")
        AppCompatDelegate.setApplicationLocales(newLocale)
    }

    // Function to open Instagram
    fun openInstagram() {
        val username = "abdohigh"
        val intent = Intent(Intent.ACTION_VIEW)
        try {
            if (context.packageManager.getPackageInfo("com.instagram.android", 0) != null) {
                intent.data = Uri.parse("http://instagram.com/_u/$username")
                intent.setPackage("com.instagram.android")
            } else {
                 intent.data = Uri.parse("http://instagram.com/$username")
            }
        } catch (e: Exception) {
            intent.data = Uri.parse("http://instagram.com/$username")
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // Instagram Button (Using generic icon since we might not have vector asset, assuming Settings for now or just Text)
                    TextButton(onClick = { openInstagram() }) {
                        Text(stringResource(R.string.follow_me), color = MaterialTheme.colorScheme.primary)
                    }

                    // Language Toggle
                    TextButton(onClick = { toggleLanguage() }) {
                        Text(
                            text = stringResource(R.string.language_toggle),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Text(
                text = if (isVpnActive) stringResource(R.string.vpn_active) else stringResource(R.string.vpn_off),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isVpnActive) Color.Green else Color.Red
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isVpnActive) {
                        viewModel.stopVpn()
                        isVpnActive = false
                    } else {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnLauncher.launch(intent)
                        } else {
                            viewModel.startVpn()
                            isVpnActive = true
                        }
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVpnActive) Color.Red else Color(0xFF006400) // Dark Green
                ),
                modifier = Modifier.size(200.dp)
            ) {
                Text(
                    text = if (isVpnActive) stringResource(R.string.stop) else stringResource(R.string.start),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            OutlinedButton(
                onClick = onNavigateToBlockList
            ) {
                Text(stringResource(R.string.manage_blocklist))
            }
        }
    }
}
