package al.terraparcel.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import al.terraparcel.domain.Preferences

/** Basemap presets supported by ParcelMap. */
@Composable
fun MapLayerSelector(p: Preferences, change: (Preferences) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("OpenStreetMap", "Satellite", "Topographic").forEach { layer ->
            FilterChip(
                selected = p.layer == layer,
                onClick = { change(p.copy(layer = layer, onlineMaps = true)) },
                label = { Text(layer) }
            )
        }
    }
}
