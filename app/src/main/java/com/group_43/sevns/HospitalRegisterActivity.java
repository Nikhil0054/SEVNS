package com.group_43.sevns;

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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HospitalRegisterActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private EditText editName, editEmail, editPassword, editAddress, editPhone;
    private TextView tvLocation;
    private Button btnRegister;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastKnownLocation;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_register);

        editName = findViewById(R.id.editHospitalName);
        editEmail = findViewById(R.id.editHospitalEmail);
        editPassword = findViewById(R.id.editHospitalPassword);
        editAddress = findViewById(R.id.editHospitalAddress);
        editPhone = findViewById(R.id.editHospitalPhone);
        tvLocation = findViewById(R.id.tvHospitalLocation);
        btnRegister = findViewById(R.id.btnRegisterHospital);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mAuth = FirebaseAuth.getInstance();

        setupLocationCallback();

        btnRegister.setOnClickListener(v -> registerHospital());

        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );

        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startLocationUpdates();

            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
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

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000
        ).setMinUpdateIntervalMillis(500).build();

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                getMainLooper()
        );
    }

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
                    tvLocation.setText(lat + ", " + lon);
                    editAddress.setText(address);
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                });

            } catch (Exception e) {
                runOnUiThread(() -> tvLocation.setText("Error fetching address: " + e.getMessage()));
            }
        }).start();
    }

    private void registerHospital() {

        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        String address = editAddress.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() ||
                phone.isEmpty() || address.isEmpty()) {

            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastKnownLocation == null) {
            Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        String uid = "Hospital-" + FirebaseAuth.getInstance().getCurrentUser().getUid();

                        HospitalRegisteration data = new HospitalRegisteration(
                                uid,
                                name,
                                email,
                                address,
                                phone,
                                lastKnownLocation.getLatitude(),
                                lastKnownLocation.getLongitude()
                        );

                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        db.collection("Hospitals")
                                .document(uid)   // Always use UID as doc ID
                                .set(data)
                                .addOnSuccessListener(a -> {
                                    Toast.makeText(this, "Hospital Registered Successfully!", Toast.LENGTH_LONG).show();
                                    startActivity(new Intent(this, HospitalLoginActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                );

                    } else {
                        Toast.makeText(this,
                                "Registration Error: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
    public static class HospitalRegisteration {

        public String uid;
        public String name;
        public String email;
        public String address;
        public String phone;
        public double latitude;
        public double longitude;

        // Empty constructor REQUIRED by Firebase
        public HospitalRegisteration() {
        }

        // Full constructor
        public HospitalRegisteration(String uid, String name, String email,
                                     String address, String phone,
                                     double latitude, double longitude) {

            this.uid = uid;
            this.name = name;
            this.email = email;
            this.address = address;
            this.phone = phone;
            this.latitude = latitude;
            this.longitude = longitude;
        }

    }

}
