package al.terraparcel.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import al.terraparcel.data.Preferences

/** Basemap presets supported by ParcelMap. */
@Composable
fun MapLayerSelector(p: Preferences, change: (Preferences) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("OpenStreetMap", "Satellite", "Topographic").forEach { layer ->
            FilterChip(
                selected = p.layer == layer,
                onClick = { change(p.copy(layer = layer, onlineMaps = true)) },
                label = { Text(layer) }
            )
        }
    }
}
