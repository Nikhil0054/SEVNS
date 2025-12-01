package com.group_43.sevns;

import android.app.AlertDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class HospitalDashboardActivity extends AppCompatActivity {

    private ListView listReports;
    private Button btnsignOut;
    private List<AccidentReport> activeReports;
    private ArrayAdapter<AccidentReport> adapter;

    private String nearestDocumentId = null;
    private AccidentReport nearestAccident = null;

    // Hospital location (example)
    double hospitalLat = 31.1048;
    double hospitalLng = 77.1734;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital_dashboard);

        listReports = findViewById(R.id.listReports);
        btnsignOut = findViewById(R.id.btnsignOut);

        activeReports = new ArrayList<>();

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                activeReports
        );

        listReports.setAdapter(adapter);

        fetchReports();

        btnsignOut.setOnClickListener(v -> signOut());

        listReports.setOnItemClickListener((parent, view, position, id) -> {
            AccidentReport selectedReport = activeReports.get(position);
            showReportActionsDialog(selectedReport);
        });
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    //FETCH NEAREST & ALL PENDING ACCIDENTS
    private void fetchReports() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Accidents")
                .whereEqualTo("status", "Pending")
                .addSnapshotListener((value, error) -> {

                    if (error != null || value == null) return;

                    activeReports.clear();
                    nearestDocumentId = null;
                    nearestAccident = null;

                    double minDistance = Double.MAX_VALUE;

                    for (DocumentSnapshot doc : value) {
                        AccidentReport accident = doc.toObject(AccidentReport.class);
                        activeReports.add(accident);

                        double distance = getDistance(
                                hospitalLat, hospitalLng,
                                accident.getLatitude(), accident.getLongitude()
                        );

                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestAccident = accident;
                            nearestDocumentId = doc.getId();
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (nearestAccident != null) {
                        Log.d("HOSPITAL", "Nearest accident ID = " + nearestDocumentId);
                    }

                    Toast.makeText(this,
                            activeReports.isEmpty() ? "No new active cases." :
                                    activeReports.size() + " active reports loaded.",
                            Toast.LENGTH_SHORT).show();
                });
    }


    // Distance calculator
    private double getDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000;
    }


    // Show dialog when hospital taps a case
    private void showReportActionsDialog(AccidentReport report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Case ID: " + report.getId() +
                        "\nStatus: " + report.getStatus())
                .setMessage(
                        "Details:\n" + report.getDescription() +
                        "\n\nContact: " + report.getPhoneNumber() +
                        "\n\nTimestamp: " + report.getTimestamp() +
                        "\n\nLocation: " + report.getLatitude() + ", " + report.getLongitude() +
                        "\n\nAddress: " + report.getAddress())
                .setNeutralButton("Close", (dialog, id) -> dialog.dismiss());

        if ("Pending".equals(report.getStatus())) {
            builder.setPositiveButton("Acknowledge", (dialog, id) -> acknowledge());
        }

        builder.create().show();
    }

    String hospitalId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    private void acknowledge() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (nearestDocumentId == null) {
            Toast.makeText(this, "No nearest accident found!", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Accidents")
                .document(nearestDocumentId)
                .update(
                        "status", "Acknowledged",
                        "driverId", "DRIVER_101",
                        "hospitalId", hospitalId
                )
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Accident Accepted!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());

        fetchReports();
    }
}
