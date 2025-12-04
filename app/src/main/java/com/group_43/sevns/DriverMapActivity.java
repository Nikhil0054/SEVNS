package com.group_43.sevns;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DriverMapActivity extends AppCompatActivity {

    private MapView map;
    private TextView tvEta;
    private Button btnComplete, btndriverSignOut;
    private String DocumentId;
    private AccidentReport assignedReport;

    private MyLocationNewOverlay myLocationOverlay;
    private Polyline roadOverlay;
    private GeoPoint currentLocation;
    private GeoPoint accidentLocation;

    // GraphHopper
    private static final String GH_API_KEY = "8c9870a4-4437-4685-9517-24cd9f46fdb8";
    private static final String GH_BASE_URL = "https://graphhopper.com/api/1/route";

    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // For tracking location changes
    private boolean isFirstLocationUpdate = true;
    private Handler routeUpdateHandler = new Handler(Looper.getMainLooper());
    private static final long ROUTE_UPDATE_INTERVAL = 30000; // 30 seconds
    private static final float MIN_DISTANCE_FOR_UPDATE = 50f; // 50 meters
    private GeoPoint lastRouteUpdateLocation;

    private boolean isRouteBeingCalculated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(
                ctx,
                PreferenceManager.getDefaultSharedPreferences(ctx)
        );

        setContentView(R.layout.driver_screen);

        tvEta = findViewById(R.id.tvEta);
        btnComplete = findViewById(R.id.btnComplete);
        btndriverSignOut = findViewById(R.id.toolbar);
        map = findViewById(R.id.map);

        // Check for null views
        if (map == null || tvEta == null || btnComplete == null) {
            Toast.makeText(this, "Error: UI components not found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        requestPermissionsIfNecessary(REQUIRED_PERMISSIONS);

        findAndDisplayAssignedReport();

        btnComplete.setOnClickListener(v -> markReportCompleted());
        btnComplete.setEnabled(false);

        if (btndriverSignOut != null) {
            btndriverSignOut.setOnClickListener(v -> signOut());
        }
    }

    @SuppressLint("SetTextI18n")
    private void findAndDisplayAssignedReport() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Accidents")
                .whereEqualTo("status", "Acknowledged")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {

                        DocumentSnapshot document = task.getResult()
                                .getDocuments()
                                .get(0);

                        assignedReport = document.toObject(AccidentReport.class);
                        DocumentId = document.getId();

                        if (assignedReport != null) {
                            tvEta.setText("Accident Location Found! Plotting route...");
                            btnComplete.setEnabled(true);
                            setupMapAndRoute();
                        } else {
                            tvEta.setText("Error loading accident data.");
                            btnComplete.setEnabled(false);
                        }
                    } else {
                        tvEta.setText("No assigned case to you.");
                        Toast.makeText(this,
                                "No assigned Case.",
                                Toast.LENGTH_LONG).show();
                        btnComplete.setEnabled(false);
                    }
                })
                .addOnFailureListener(e -> {
                    if (tvEta != null) {
                        tvEta.setText("Error loading data.");
                    }
                    Toast.makeText(this,
                            "Error fetching data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void setupMapAndRoute() {
        if (assignedReport == null || map == null) return;

        try {
            map.setTileSource(TileSourceFactory.MAPNIK);
            map.setBuiltInZoomControls(true);
            map.setMultiTouchControls(true);

            IMapController ctl = map.getController();
            ctl.setZoom(15.0);

            accidentLocation = new GeoPoint(
                    assignedReport.getLatitude(),
                    assignedReport.getLongitude()
            );
            ctl.setCenter(accidentLocation);

            Marker m = new Marker(map);
            m.setPosition(accidentLocation);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("Accident: " + assignedReport.getDescription()
                    + "\n\n" + assignedReport.getAddress());
            map.getOverlays().add(m);

            // Set up location tracking with proper listener
            setupLocationTracking();

            map.invalidate();
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up map: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setupLocationTracking() {
        try {
            GpsMyLocationProvider gpsProvider = new GpsMyLocationProvider(this);
            gpsProvider.setLocationUpdateMinTime(2000); // 2 seconds
            gpsProvider.setLocationUpdateMinDistance(5); // 5 meters

            myLocationOverlay = new MyLocationNewOverlay(gpsProvider, map);
            myLocationOverlay.enableMyLocation();
            myLocationOverlay.enableFollowLocation();

            // Add custom location listener
            myLocationOverlay.runOnFirstFix(() -> {
                Location location = myLocationOverlay.getLastFix();
                if (location != null) {
                    runOnUiThread(() -> onLocationUpdate(location));
                }
            });

            map.getOverlays().add(myLocationOverlay);

            // Start periodic location checks
            startLocationUpdateTimer();

        } catch (Exception e) {
            Toast.makeText(this, "Error setting up location: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocationUpdateTimer() {
        routeUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (myLocationOverlay != null) {
                    Location location = myLocationOverlay.getLastFix();
                    if (location != null) {
                        onLocationUpdate(location);
                    }
                }
                routeUpdateHandler.postDelayed(this, 5000); // Check every 5 seconds
            }
        }, 5000);
    }

    private void onLocationUpdate(Location location) {
        if (location == null || accidentLocation == null || isRouteBeingCalculated) return;

        currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        // First location update - get initial route
        if (isFirstLocationUpdate) {
            isFirstLocationUpdate = false;
            lastRouteUpdateLocation = currentLocation;

            ArrayList<GeoPoint> points = new ArrayList<>();
            points.add(currentLocation);
            points.add(accidentLocation);
            BoundingBox box = BoundingBox.fromGeoPoints(points);
            map.zoomToBoundingBox(box, true);

            tvEta.setText("Calculating route from your location...");
            calculateRoute(currentLocation, accidentLocation);
        } else {
            // Update route if moved significantly
            if (shouldUpdateRoute(currentLocation)) {
                lastRouteUpdateLocation = currentLocation;
                tvEta.setText("Recalculating route...");
                calculateRoute(currentLocation, accidentLocation);
            }
        }
    }

    private boolean shouldUpdateRoute(GeoPoint newLocation) {
        if (lastRouteUpdateLocation == null) return true;

        float distance = (float) lastRouteUpdateLocation.distanceToAsDouble(newLocation);
        return distance >= MIN_DISTANCE_FOR_UPDATE;
    }

    private void calculateRoute(GeoPoint start, GeoPoint end) {
        if (isRouteBeingCalculated) return;
        isRouteBeingCalculated = true;
        new Thread(() -> {
            fetchGraphHopperRoute(start, end);
            isRouteBeingCalculated = false;
        }).start();
    }

    // ---------------- GRAPHHOPPER ROUTING ----------------
    private void fetchGraphHopperRoute(GeoPoint start, GeoPoint end) {
        if (start == null || end == null) {
            showStraightLineFallback(end, start);
            runOnUiThread(() -> tvEta.setText("ETA: unavailable (invalid locations)"));
            return;
        }

        OkHttpClient client = new OkHttpClient();

        String url = GH_BASE_URL
                + "?point=" + start.getLatitude() + "," + start.getLongitude()
                + "&point=" + end.getLatitude() + "," + end.getLongitude()
                + "&vehicle=car&locale=en&instructions=false&points_encoded=false"
                + "&key=" + GH_API_KEY;

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                final String errorMsg = "HTTP " + response.code() + ": " + response.message();
                runOnUiThread(() -> Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show());
                showStraightLineFallback(end, start);
                runOnUiThread(() -> tvEta.setText("ETA: unavailable (using straight line)"));
                return;
            }

            String body = response.body() != null ? response.body().string() : "";

            if (body.isEmpty()) {
                showStraightLineFallback(end, start);
                runOnUiThread(() -> tvEta.setText("ETA: unavailable (empty response)"));
                return;
            }

            JSONObject json = new JSONObject(body);
            JSONArray paths = json.getJSONArray("paths");

            if (paths.length() == 0) {
                showStraightLineFallback(end, start);
                runOnUiThread(() -> tvEta.setText("ETA: unavailable (no route found)"));
                return;
            }

            JSONObject firstPath = paths.getJSONObject(0);
            JSONObject points = firstPath.getJSONObject("points");
            JSONArray coords = points.getJSONArray("coordinates");

            ArrayList<GeoPoint> routePoints = new ArrayList<>();
            for (int i = 0; i < coords.length(); i++) {
                JSONArray c = coords.getJSONArray(i);
                double lon = c.getDouble(0);
                double lat = c.getDouble(1);
                routePoints.add(new GeoPoint(lat, lon));
            }

            final int minutes;
            final double distanceKm;
            if (firstPath.has("time")) {
                long timeMs = firstPath.getLong("time");
                minutes = (int) (timeMs / 1000 / 60);
            } else {
                minutes = -1;
            }

            if (firstPath.has("distance")) {
                distanceKm = firstPath.getDouble("distance") / 1000.0;
            } else {
                distanceKm = -1;
            }

            runOnUiThread(() -> {
                if (map == null) return;

                if (roadOverlay != null) {
                    map.getOverlays().remove(roadOverlay);
                }
                roadOverlay = new Polyline(map);
                roadOverlay.setPoints(routePoints);
                roadOverlay.setWidth(10f);
                roadOverlay.setColor(0xFF2196F3);
                map.getOverlays().add(roadOverlay);
                map.invalidate();

                if (minutes >= 0 && distanceKm >= 0) {
                    tvEta.setText(String.format("ETA: ~%d mins (%.1f km)", minutes, distanceKm));
                } else if (minutes >= 0) {
                    tvEta.setText("ETA: ~" + minutes + " mins");
                } else {
                    tvEta.setText("ETA: available");
                }
            });

        } catch (IOException ioe) {
            showStraightLineFallback(end, start);
            runOnUiThread(() -> {
                if (tvEta != null) {
                    tvEta.setText("ETA: unavailable");
                }
                Toast.makeText(DriverMapActivity.this,
                        "Network error: " + ioe.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            showStraightLineFallback(end, start);
            runOnUiThread(() -> {
                if (tvEta != null) {
                    tvEta.setText("ETA: unavailable");
                }
                Toast.makeText(DriverMapActivity.this,
                        "Routing error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showStraightLineFallback(GeoPoint accidentPoint, GeoPoint myLoc) {
        if (accidentPoint == null || myLoc == null) return;

        runOnUiThread(() -> {
            if (map == null) return;

            if (roadOverlay != null) {
                map.getOverlays().remove(roadOverlay);
            }
            roadOverlay = new Polyline(map);
            ArrayList<GeoPoint> pts = new ArrayList<>();
            pts.add(myLoc);
            pts.add(accidentPoint);
            roadOverlay.setPoints(pts);
            roadOverlay.setWidth(8f);
            roadOverlay.setColor(0xFFFF0000);
            map.getOverlays().add(roadOverlay);
            map.invalidate();
        });
    }

    // ---------------- COMPLETE BUTTON ----------------
    private void markReportCompleted() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (assignedReport == null || DocumentId == null) {
            Toast.makeText(this,
                    "No assigned case to complete.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Accidents")
                .document(DocumentId)
                .update("status", "Completed")
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this,
                            "Report marked COMPLETED.",
                            Toast.LENGTH_SHORT).show();
                    // Reset for next assignment
                    isFirstLocationUpdate = true;
                    lastRouteUpdateLocation = null;
                    assignedReport = null;
                    DocumentId = null;
                    findAndDisplayAssignedReport();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // ---------------- PERMISSION HANDLER ----------------
    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> req = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                req.add(p);
            }
        }
        if (!req.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    req.toArray(new String[0]),
                    REQUEST_PERMISSIONS_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this,
                        "Map needs Location permission.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (routeUpdateHandler != null) {
            routeUpdateHandler.removeCallbacksAndMessages(null);
        }
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}