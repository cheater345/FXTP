package com.fxtp.agent;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText pinInput, serverInput;
    private Button saveBtn, backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        pinInput = findViewById(R.id.pinInput);
        serverInput = findViewById(R.id.serverInput);
        saveBtn = findViewById(R.id.saveBtn);
        backBtn = findViewById(R.id.backBtn);

        saveBtn.setOnClickListener(v -> {
            String pin = pinInput.getText().toString().trim();
            String server = serverInput.getText().toString().trim();
            getSharedPreferences("fxtp_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("pin", pin)
                    .putString("server", server)
                    .apply();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });

        backBtn.setOnClickListener(v -> finish());
    }
}
