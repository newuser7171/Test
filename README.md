# TerraParcel — native Android land measurement

Original Kotlin / Jetpack Compose app for measuring and saving points, lines and parcels. No WebView, account, ads, analytics or coordinate-upload service.

## Build and APK
JDK 17, Android SDK 35, Gradle 8.9. Open this folder in Android Studio (JDK 17), or run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows: use `gradlew.bat`. The official Gradle 8.9 wrapper is included, with a pinned distribution checksum.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions builds and tests each push / PR and uploads **TerraParcel-debug-and-reports**. Download its ZIP, extract the APK and install on Android 8.0+. Enable installation from the app used to open it. The debug APK is for evaluation, not a production-signed release.

For a release, configure your own signing key through environment variables / local Gradle properties and build `assembleRelease`. Never commit signing keys.

## First measurement
1. Open Map. Enable online maps in Layers if desired; otherwise use the offline coordinate canvas.
2. Choose New polygon. Long-press to add vertices, or use the crosshair + button.
3. Tap a vertex to inspect / edit coordinates, remove it or insert a point after it. Choose Move on map, then drag the selected orange point. Each drag is one undo operation.
4. GPS Walk explains location permission, starts foreground recording and rejects fixes above the selected accuracy threshold. Keep the app visible; backgrounding pauses walking. Resume explicitly.
5. Close Parcel / Save opens parcel metadata. Enter a name and save.
6. My Parcels → tap a parcel to reopen and zoom; edit and save under the same stable ID.
7. Export or share GeoJSON, GPX, KML, KMZ or CSV. Export all includes active measurements only.

Phone measurements are **estimates, not survey-grade or official cadastral/legal boundaries**. Android horizontal accuracy is not an area-error estimate. External RTK is not connected or claimed.

## Implemented in this version
- WGS84 ellipsoidal area, perimeter, segment length and bearing via GeographicLib; JTS topology validation.
- Points / lines / polygons; live measurements, crosshair, long press, selected-vertex dragging, coordinate editing, insertion, deletion, undo/redo.
- GPS foreground walking with accuracy / spacing filters, pause/resume, stale-fix rejection, actual Android accuracy, altitude, speed and course over ground.
- Room parcel, normalized point, measurement, category, photo and settings tables. Transactional saves, draft recovery, non-destructive migration.
- Search, sort, rename through Edit → Save, duplicate, favorites, metadata, colors, multi-parcel display and trash / restore / permanent deletion.
- Native MapLibre renderer, compass, scale, current location, coordinate navigation, raster provider abstraction and configurable HTTPS XYZ / compatible WMS / WMTS templates.
- WGS84 and UTM display (including Norway / Svalbard zone exceptions); coordinate copy.
- Local photos on parcels or points through Android's document picker; photo gallery and timestamps. No media-library or camera permission.
- GeoJSON, GPX, KML/KMZ, CSV import/export; sharing; logical database + photo ZIP backup, merge restore and automatic rolling local backups.
- Light / dark / automatic themes; metric defaults and area / distance units; English and Albanian important controls.
- Home dashboard and native map preview; offline saved geometry access, GPS, edits and exports.
- Recorded altitude profile when every route vertex has elevation; map snapshot sharing; navigation-app handoff.

## Important limits / future work
See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/STATUS.md](docs/STATUS.md). No fake external GNSS status, satellite subscription, ASIG catalogue, MGRS or offline-region download button.
- Single-ring polygons only: holes / multi-geometries are rejected, never flattened silently.
- GPS walking is foreground only and deliberately pauses in background. No background / notification permission.
- Online layers require network and appropriate provider licensing. Offline regions are not downloaded in this version.
- Snapshots contain the map renderer, not Compose measurement panels; verify attribution when sharing.
- Photos are copied privately; EXIF location is not read. Uninstall removes local data. Export a backup externally.
- English fallback remains for technical diagnostics.
- Imported GPX tracks are separate lines unless explicitly marked POLYGON. CSV without shape defaults to polygon for multiple coordinates.
- Settings support one custom base layer at a time, not a multi-service GIS catalogue.
- Maps render in Web Mercator; polar display and world-spanning polygon editing are outside this MVP. Measurement math itself is geodesic.

## Validation
CI is the authoritative build result, not this README. Tests cover reference geodesic area/perimeter/distance, unit and UTM conversion, dateline geometry, invalid polygons, restart persistence, interchange formats and backup/restore. Real-device GPS, touch editing, map provider availability and Android permission behavior still require field testing.

## Sources
- [MapLibre Android](https://maplibre.org/maplibre-native/android/examples/): native renderer and replaceable raster/vector sources.
- [GeographicLib PolygonArea](https://geographiclib.sourceforge.io/html/java/net/sf/geographiclib/PolygonArea.html): WGS84 geodesic computations.
- [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/): standard map attribution and usage constraints.

Original code; no branding, source or assets copied from the inspiration apps.
