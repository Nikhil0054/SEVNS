package com.group_43.sevns;
//new
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.Priority;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class AccidentReportingActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private TextView addressTextView, tvStatus;
    private EditText editPhone, editDesc;
    private Button btnReport;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;
    private LocationCallback locationCallback;
    private String currentReportId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accident_reporting);

        // ---------- UI INITIALIZATION ----------
        addressTextView = findViewById(R.id.addressTextView);
        tvStatus = findViewById(R.id.tvStatus);
        editPhone = findViewById(R.id.editPhone);
        editDesc = findViewById(R.id.editDesc);
        btnReport = findViewById(R.id.btnReport);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnReport.setEnabled(false);

        // Callbacks and permissions
        setupLocationCallback();
        requestLocationPermission();

        btnReport.setOnClickListener(v -> submitReport());
    }


    // PERMISSIONS
    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            startLocationUpdates();
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission denied. Cannot report accident.", Toast.LENGTH_LONG).show();
                addressTextView.setText("Location permission denied.");
                btnReport.setEnabled(false);
            }
        }
    }
// LOCATION HANDLING
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                lastKnownLocation = locationResult.getLastLocation();
                if (lastKnownLocation != null) {
                    double lat = lastKnownLocation.getLatitude();
                    double lon = lastKnownLocation.getLongitude();
                    getAddressFromCoordinates(lat, lon);
                }
            }
        };
    }

    private void startLocationUpdates() {

        LocationRequest locationRequest =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        1000
                )
                        .setMinUpdateIntervalMillis(500)
                        .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    // REVERSE GEOCODING
    @SuppressLint("SetTextI18n")
    private void getAddressFromCoordinates(double lat, double lon) {

        new Thread(() -> {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/reverse?lat=" + lat + "&lon=" + lon + "&format=json";

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");

                // IMPORTANT: Required by Nominatim
                conn.setRequestProperty("User-Agent", "HospitalApp/1.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null)
                    result.append(line);

                reader.close();

                JSONObject json = new JSONObject(result.toString());
                String address = json.optString("display_name");

                runOnUiThread(() -> {
                    addressTextView.setText(address);
                    btnReport.setEnabled(true);

                    // Stop further location requests (avoid rate-limit)
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                });

            } catch (Exception e) {
                runOnUiThread(() -> addressTextView.setText("Error fetching address: " + e.getMessage()));
            }
        }).start();
    }


    // REPORT SUBMISSION

    private static String generateCaseId() {
        int randomNum = (int) (Math.random() * 90000) + 10000; // 5-digit
        return "CASE" + randomNum;
    }

    private void createUniqueCaseId(FirebaseFirestore db, OnCaseIdGenerated callback) {

        String newCaseId = generateCaseId();

        db.collection("Accidents")
                .whereEqualTo("id", newCaseId)
                .get()
                .addOnSuccessListener(query -> {

                    if (query.isEmpty()) {
                        // Unique ID Found
                        callback.onGenerated(newCaseId);
                    } else {
                        // Duplicate → generate again
                        createUniqueCaseId(db, callback);
                    }

                })
                .addOnFailureListener(e ->
                        callback.onGenerated(null) // error
                );
    }

    public interface OnCaseIdGenerated {
        void onGenerated(String caseId);
    }
    @SuppressLint("SetTextI18n")
    private void submitReport() {
        if (lastKnownLocation == null) {
            Toast.makeText(this, "Please wait for location to be fetched.", Toast.LENGTH_SHORT).show();
            return;
        }

        String phone = editPhone.getText().toString().trim();
        String description = editDesc.getText().toString().trim();

        if (phone.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "Please enter phone number and description.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        createUniqueCaseId(db, caseId -> {

            if (caseId == null) {
                Toast.makeText(this, "Error generating Case ID!", Toast.LENGTH_SHORT).show();
                return;
            }

        AccidentReport data = new AccidentReport(
                caseId,
                lastKnownLocation.getLatitude(),
                lastKnownLocation.getLongitude(),
                phone,
                description,
                addressTextView.getText().toString(),
                "",
                System.currentTimeMillis(),
                "",
                ""
        );

            tvStatus.setText("Status: Report submitted! ID: " + caseId);
            btnReport.setEnabled(false);

        db.collection("Accidents")
                .document(caseId)
                .set(data)
                .addOnSuccessListener(documentReference ->
                        Toast.makeText(this, "Data sent to Nearest Hospital!", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );

        Intent intent = new Intent(this, AccidentStatusActivity.class);
        intent.putExtra("CASE_ID", caseId);
        startActivity(intent);
        finish();
        });
    }
}
