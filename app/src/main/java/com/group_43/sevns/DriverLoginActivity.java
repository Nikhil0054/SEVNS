package com.group_43.sevns;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class DriverLoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private Button btnLogin, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_login);

        editEmail = findViewById(R.id.email);
        editPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        btnLogin.setOnClickListener(v -> handleLogin());
        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, DriverRegister.class)));
    }

    private void handleLogin() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Sign in with Firebase Authentication
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        String uid = "Driver-" + FirebaseAuth.getInstance().getCurrentUser().getUid();
                        checkDriverInFirestore(uid);

                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(DriverLoginActivity.this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void checkDriverInFirestore(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Drivers")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {

                        String storedUid = document.getString("Driver_ID");

                        if (storedUid != null && storedUid.startsWith("DRIVER")) {

                            Toast.makeText(this, "Driver Login Successful", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(DriverLoginActivity.this, DriverMapActivity.class));
                            finish();
                        } else {
                            FirebaseAuth.getInstance().signOut();
                            Toast.makeText(this, "Not a Driver account", Toast.LENGTH_SHORT).show();
                        }

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
