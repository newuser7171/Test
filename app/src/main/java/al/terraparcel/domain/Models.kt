package al.terraparcel.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Vertex(
    val lat: Double, val lon: Double,
    val altitude: Double? = null, val accuracy: Float? = null,
    val time: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString()
) {
    init { require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { "Invalid WGS84 coordinates" } }
}
@Serializable enum class Shape { POINT, LINE, POLYGON }
@Serializable
data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "", val notes: String = "", val owner: String = "",
    val category: String = "Property", val color: String = "#167B62",
    val shape: Shape = Shape.POLYGON, val points: List<Vertex> = emptyList(),
    val created: Long = System.currentTimeMillis(), val favorite: Boolean = false
)
@Serializable
data class Preferences(
    val language: String = "en", val theme: String = "auto",
    val areaUnit: String = "m²", val lengthUnit: String = "m",
    val maxAccuracy: Float = 5f, val minSpacing: Float = 2f,
    val onlineMaps: Boolean = false, val layer: String = "OpenStreetMap",
    val customTiles: String = "", val attribution: String = "",
    val showSaved: Boolean = true,
    // Additive JSON defaults preserve existing settings and backups; Room schema is unchanged.
    val cadastreEnabled: Boolean = false, val cadastreOpacity: Float = 1f,
    val customLayerName: String = "",
    val customOverlayEnabled: Boolean = false,
    val customOverlayName: String = "Custom overlay",
    val customOverlayTiles: String = "", val customOverlayAttribution: String = ""
) {
    init {
        require(language in listOf("en","sq") && theme in listOf("auto","light","dark")){"Unsupported language or theme"}
        require(areaUnit in listOf("m²","ha","ac","km²") && lengthUnit in listOf("m","km","ft","mi")){"Unsupported units"}
        require(maxAccuracy.isFinite() && maxAccuracy in 1f..100f && minSpacing.isFinite() && minSpacing in 0.5f..100f){"Invalid GPS recording settings"}
        require(customTiles.isEmpty() || customTiles.startsWith("https://")){"Map URLs must use HTTPS"}
        require(customOverlayTiles.isEmpty() || customOverlayTiles.startsWith("https://")){"Map URLs must use HTTPS"}
        require(cadastreOpacity.isFinite() && cadastreOpacity in 0f..1f){"Invalid overlay opacity"}
    }
}
val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
