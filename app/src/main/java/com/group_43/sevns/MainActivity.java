package com.group_43.sevns;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnUser = findViewById(R.id.btnUser);
        Button btnHospital = findViewById(R.id.btnHospital);
        Button btnAmbulance = findViewById(R.id.btnAmbulance);
        Button btnHospitalRegister = findViewById(R.id.btnHospitalRegister);
        Button btntrackCase = findViewById(R.id.btntrackCase);
        Button btnDriverRegister = findViewById(R.id.btnDriverRegister);



        btnUser.setOnClickListener(v ->
                startActivity(new Intent(this, AccidentReportingActivity.class)));

        btnHospitalRegister.setOnClickListener(v ->
                startActivity(new Intent(this, HospitalRegisterActivity.class)));

        btnHospital.setOnClickListener(v ->
                startActivity(new Intent(this, HospitalLoginActivity.class)));

        btnAmbulance.setOnClickListener(v ->
                startActivity(new Intent(this, DriverLoginActivity.class)));

        btntrackCase.setOnClickListener(v ->
                startActivity(new Intent(this, AccidentStatusActivity.class)));

        btnDriverRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, DriverRegister.class));
        });
        }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);   // Prevents going to previous activity
    }
}
