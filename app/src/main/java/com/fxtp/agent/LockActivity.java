package com.fxtp.agent;

import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LockActivity extends AppCompatActivity {

    private EditText pinInput;
    private Button unlockBtn;
    private TextView errorText;
    private WebView webView;
    private View lockView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen lock
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.activity_lock);

        lockView = findViewById(R.id.lockView);
        webView = findViewById(R.id.webView);
        pinInput = findViewById(R.id.pinInput);
        unlockBtn = findViewById(R.id.unlockBtn);
        errorText = findViewById(R.id.errorText);

        // Check if we need to display HTML
        boolean displayHtml = getIntent().getBooleanExtra("display_html", false);
        String htmlContent = getIntent().getStringExtra("html_content");

        if (displayHtml && htmlContent != null) {
            lockView.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient());
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
        } else {
            lockView.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
        }

        // Hide navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        unlockBtn.setOnClickListener(v -> {
            String entered = pinInput.getText().toString().trim();
            SharedPreferences prefs = getSharedPreferences("fxtp_prefs", MODE_PRIVATE);
            String storedPin = prefs.getString("pin", "1234");
            if (entered.equals(storedPin)) {
                finish();
            } else {
                errorText.setText("Incorrect PIN");
                pinInput.setText("");
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Block back button
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
