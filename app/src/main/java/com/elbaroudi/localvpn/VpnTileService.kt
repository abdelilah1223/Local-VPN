package com.elbaroudi.localvpn

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class VpnTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val tile = qsTile
        val isRunning = AdBlockVpnService.isServiceRunning
        
        if (isRunning) {
            // Stop
            val intent = Intent(this, AdBlockVpnService::class.java)
            intent.action = AdBlockVpnService.ACTION_STOP
            startService(intent)
            tile.state = Tile.STATE_INACTIVE
        } else {
            // Start
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // Must open activity to ask permission
                val activityIntent = Intent(this, MainActivity::class.java)
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activityIntent.action = AdBlockVpnService.ACTION_START
                startActivityAndCollapse(activityIntent)
            } else {
                // Already prepared, just start
                val intent = Intent(this, AdBlockVpnService::class.java)
                intent.action = AdBlockVpnService.ACTION_START
                startService(intent)
                tile.state = Tile.STATE_ACTIVE
            }
        }
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        val isRunning = AdBlockVpnService.isServiceRunning
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
