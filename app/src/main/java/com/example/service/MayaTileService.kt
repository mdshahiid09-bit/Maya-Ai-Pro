package com.example.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.MayaApplication

class MayaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Talk to Maya"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val app = application as? MayaApplication
        app?.audioPipeline?.playChime()
        app?.geminiLiveClient?.startSession()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_AUTO_START_TALK", true)
        }
        startActivityAndCollapse(intent)
    }
}
