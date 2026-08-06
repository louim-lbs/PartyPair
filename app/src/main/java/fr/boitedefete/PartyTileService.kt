package fr.boitedefete

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Raccourci dans le volet des reglages rapides.
 * Reprend la bascule de l'ecran principal : allumer, puis mettre en veille.
 */
@RequiresApi(Build.VERSION_CODES.N)
class PartyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings(this).isConfigured) return
        val ready = PartyService.state.value.step == Step.READY
        PartyService.start(
            this,
            if (ready) PartyService.ACTION_POWER_OFF else PartyService.ACTION_START
        )
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val ready = PartyService.state.value.step == Step.READY
        tile.state = if (ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        runCatching { tile.updateTile() }
    }
}
