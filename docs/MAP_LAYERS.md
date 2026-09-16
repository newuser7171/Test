# Satellite and Albania Cadastre

In Settings → Map layers, enable online maps, select Satellite (or OpenStreetMap), and enable Albania Cadastre. Opacity defaults to 100%. Basemap selection and overlays are independent. The online switch suppresses all network raster sources while retaining selections and editable measurements.

## Implementation

`MapLayers.kt` builds ordered raster sources/layers: background → basemap → cadastre → optional custom overlay. `ParcelMap.kt` appends measurement fills, white line halos, colored lines and vertex/GPS circles above every raster layer. Compose controls remain above the native map. Layers never handle gestures.

The Esri URL is `https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`. Source metadata verified 2026-09-16 reports “Source: Esri, Vantor, Earthstar Geographics, and the GIS User Community”. The app refreshes `copyrightText` from the service metadata at most daily when online Satellite is selected, with the verified credit as its first-run fallback. Full credits are displayed on the map, native attribution, and screenshot exports. Metadata requests contain no viewport or parcel geometry.

ASIG WMS uses version 1.1.1, layer `p_kadas_albscad_072026`, default style, `SRS=EPSG:900913`, `TRANSPARENT=TRUE`, PNG and 256×256 tiles. MapLibre expands `{bbox-epsg-3857}` itself; 900913 and 3857 share Web Mercator values, so no additional coordinate conversion or axis swap is introduced. Orange parcel styling remains entirely server-controlled. Attribution is © ASIG / ASHK.

Capabilities verified 2026-09-16 specify coverage [19.2389271695,39.6403123885,21.0904541333,42.6613141789] and scale denominators 200–50001. Sources request 256px tile zooms 14–21 (approximately 1:34124–266 in Web Mercator); the raster layer is visible at MapLibre camera zooms [13,21), whose world uses 512px tiles. Source maxzoom prevents requests beyond the useful service scale, with native overzoom at the upper end. Outside the useful zoom range the basemap and measurements remain visible.

## Persistence, lifecycle, and networking

New optional preferences have serialization defaults and are stored in the existing Room AppSettings JSON. Old custom basemap names/URLs/credits decode unchanged. Separate custom-overlay settings prevent a new overlay overwriting an existing custom basemap. The Room schema does not change, and existing backups remain readable.

MapLibre handles viewport tile selection, cancellation and normal tile caching. There is no bulk fetch, tile timestamp cache busting, dataset download, or per-GPS-fix raster refresh. Style effects run only when generated style JSON changes. Superseded callbacks are ignored; each new style gets exactly one set of measurement sources/layers. Opacity changes commit on slider release rather than every drag event. A failed WMS tile leaves its transparent layer absent; it never replaces or clears the basemap or edits user geometry.

This branch also carries forward the unmerged 0.1.2 startup initialization and first-vertex/visibility fixes, preserving main's newer basemap presets. No measurement database, location, geodesic, import/export, or search logic is replaced.

## Verification

- JVM tests cover independent basemap/overlay combinations, WMS request parameters, bounds/zoom/opacity, offline mode, old settings compatibility, credits and Room settings restart.
- Android tests launch the real Activity and renderer, query measurement features, check layer ordering and uniqueness, switch basemaps, toggle Cadastre, pan/zoom, change opacity and disable online maps. The live layer test observes real HTTP image responses and saves native map screenshots to its artifact.
- `python scripts/verify_map_services.py` (requires Pillow) requests one tile from each live service, verifies raster decoding, ASIG transparency and visible boundaries, and saves metadata/tile evidence in `app/build/verification/services/`.
- CI runs assembleDebug, JVM tests, lint and Android 15 emulator tests. Provider outages can fail the live service test independently of the offline tests.

## Limitations

No official cadastral accuracy is inferred from imagery alignment. Government reference layers remain separate from user-created phone measurements. Service availability, completeness, imagery age and resolution are provider-dependent. The selected ASIG layer name and advertised scale limits are versioned and may need a future update. Native Web Mercator alignment is tested; this does not establish ground-survey agreement between imagery and cadastral boundaries. Downloadable offline satellite/cadastral regions are not implemented.
