package com.group_43.sevns;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class DriverRegister extends AppCompatActivity {

    private EditText editName, editEmail, editPassword, editAddress, editPhone;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_register);
        editName = findViewById(R.id.editDriverName);
        editEmail = findViewById(R.id.editDriverEmail);
        editPassword = findViewById(R.id.editDriverPassword);
        editPhone = findViewById(R.id.editDriverPhone);
        Button btnRegister = findViewById(R.id.btnRegisterDriver);

        mAuth = FirebaseAuth.getInstance();

        btnRegister.setOnClickListener(v -> registerDriver());
    }

    private static String generateDriverId() {
        int randomNum = (int) (Math.random() * 90000) + 10000; // 5-digit
        return "DRIVER" + randomNum;
    }

    private void createUniqueCaseId(FirebaseFirestore db, OnDriverIdGenerated callback) {

        String newDriver_ID = generateDriverId();

        db.collection("Drivers")
                .whereEqualTo("Driver_ID", newDriver_ID)
                .get()
                .addOnSuccessListener(query -> {

                    if (query.isEmpty()) {
                        // Unique ID Found
                        callback.onGenerated(newDriver_ID);
                    } else {
                        // Duplicate → generate again
                        createUniqueCaseId(db, callback);
                    }

                })
                .addOnFailureListener(e ->
                        callback.onGenerated(null) // error
                );
    }

    public interface OnDriverIdGenerated {
        void onGenerated(String driverId);
    }

    private void registerDriver() {
        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() ||
                phone.isEmpty()) {

            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {
                        String doc_id = "Driver-" + FirebaseAuth.getInstance().getCurrentUser().getUid();

                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        createUniqueCaseId(db, driverid -> {
                            if (driverid == null) {
                                        Toast.makeText(this, "Error generating Driver ID!", Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                        DriverRegisteration data = new DriverRegisteration(
                                driverid,
                                name,
                                email,
                                phone,
                                "Unavailable"
                        );

                        db.collection("Drivers")
                                .document(doc_id)   // Always use UID as doc ID
                                .set(data)
                                .addOnSuccessListener(a -> {
                                    Toast.makeText(this, "Driver Registered Successfully!", Toast.LENGTH_LONG).show();
                                    startActivity(new Intent(this, DriverLoginActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                );
                        });
                    } else {
                        Toast.makeText(this,
                                "Registration Error: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });

    }

    public static class DriverRegisteration {

        public String Driver_ID;
        public String name, status;
        public String email;
        public String phone;
        // Empty constructor REQUIRED by Firebase
        public DriverRegisteration() {
        }

        // Full constructor
        public DriverRegisteration(String Driver_ID, String name, String email, String phone,  String status) {

            this.Driver_ID = Driver_ID;
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.status = status;
        }

    }
}