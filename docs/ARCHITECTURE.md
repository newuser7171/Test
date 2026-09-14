# Architecture

UI (Compose + Material 3) → LandViewModel (StateFlow, undo/draft state) → ParcelRepository → Room.

- `domain`: serializable geometry and preferences, WGS84 computations, topology validation, UTM projection. Canonical geometry uses longitude/latitude in GeoJSON and typed lat/lon internally. Values are always stored in SI units.
- `data`: six Room entities; transactions write Parcel, ParcelPoint and Measurement together. Migration 1→2 adds favorite without deleting data. New migrations must preserve both geometry and attachments; never use fallbackToDestructiveMigration.
- `map`: MapProvider emits a style; MapHandle abstracts camera operations. RasterMapProvider supports no-network canvas, OpenStreetMap, licensed XYZ, and compatible EPSG:3857 WMS GetMap URLs using {bbox-epsg-3857}. WMTS requires a provider-compatible template mapping {z}/{x}/{y} to TileMatrix/TileCol/TileRow. GetCapabilities discovery is future work.
- `location`: LocationSource boundary with PhoneLocation implementation. Future Bluetooth transport can supply Fix records with explicit receiver state and reported accuracy. No external receiver / RTK states are fabricated.
- `ui`: outdoor-size controls, lifecycle-owned native MapView, Compose dashboard/editor/parcels/settings, locale resources.

## Persistence and safety
UUID parcel and point identities survive edits. Drafts persist after a 300 ms debounce. Saves are explicit and transactional. Deleted parcels remain in trash until confirmed permanent deletion. Backups contain versioned logical database records plus actual photo bytes, independent of SQLite WAL state. Restore validates geometry and ZIP paths, caps total data, and writes new photo filenames before a database transaction.

Local automatic backup updates on mutations, retains three dated archives and is not cloud backup. Same-day snapshots are replaced. Accountless local files cannot survive uninstall by themselves.

## Privacy and map services
Online maps default off. Turning them on necessarily reveals requested tile regions to that provider, explained in the UI. No API receives parcel geometry or GPS history. No background tracking, analytics, ads or cloud SDK. Android OS cloud backup disabled.

OpenStreetMap standard raster tiles: no prefetch or bulk downloading. Native SDK caching and provider HTTP cache policy govern normal viewed tiles. Do not put private secrets into a public repository.

Government cadastral layers must be configured as reference imagery only; user-created geometry remains in separate map source + local database. ASIG integration requires independently checking service endpoints, CRS, attribution, terms and licensing; none are assumed.

## Extensions
- OfflineRegionStore behind MapProvider: provider-specific quota, terms and tile-pack lifecycle, not arbitrary standard OSM tile downloads.
- WMS/WMTS service catalogue: capabilities parser, CRS/axis order, compatible matrix sets, authentication, attribution.
- Bluetooth GNSS: separately requested Bluetooth permissions, checksum-validated NMEA, fix quality, real receiver accuracy, disconnect/stale transitions, recording source per vertex; never infer cm accuracy from RTK status alone.
- Shapefile / GeoPackage: CRS transform, holes, multipolygons, DB schema extension and lossless round trips before exposing UI.
- Cloud sync: optional explicit opt-in, encrypted transport, tombstones, immutable operation IDs and conflict resolution. Default remains local.
