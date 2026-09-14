# Scope and field validation

This is the first native MVP, not the complete advanced GIS roadmap.

Implemented paths are listed in README. Future (no working-looking controls): external NMEA / RTK transport, MGRS, bulk offline map regions, Shapefile, GeoPackage, verified ASIG catalogue, multi-layer overlays, parcel label placement, EXIF location display and camera capture. Existing document picker supports photos without camera permission.

Before field use:
1. Install debug APK; deny location and confirm manual drawing / saving still works.
2. Restart app and reopen edited parcels; check colors, notes and units.
3. Grant approximate location and verify the actual accuracy value rejects poor fixes.
4. Walk a known boundary outdoors. Pause, resume, remove a bad vertex, close and save.
5. Background the app during recording: it must pause, never silently bridge a background path.
6. Move a selected point, undo once, then redo. Test insertion and deletion.
7. Disable network and confirm GPS/geometry editing/export still work.
8. Export and reimport each supported format. Invalid polygons / holes must show errors.
9. Attach a photo, export backup, delete the parcel, restore and confirm the photo loads.
10. Confirm map attribution appears in live maps and inspect snapshots before redistribution.

Known limits: course-over-ground is the GPS Tools heading, not a magnetic compass. Map compass indicates map orientation. GPS altitude is receiver altitude, not an orthometric terrain model. Position accuracy is not propagated into a polygon area uncertainty. Custom map URLs are operator configuration and may fail because of terms, CRS, keys or service availability. Test on the actual device before relying on data.
