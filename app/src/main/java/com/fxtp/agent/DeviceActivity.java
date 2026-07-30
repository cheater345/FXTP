package com.fxtp.agent;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class DeviceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        TextView deviceInfo = findViewById(R.id.deviceInfo);
        Button btnBack = findViewById(R.id.btnBack);

        String info = "📱 Device Info\n" +
                "Model: " + android.os.Build.MODEL + "\n" +
                "Brand: " + android.os.Build.BRAND + "\n" +
                "Android: " + android.os.Build.VERSION.RELEASE + "\n" +
                "SDK: " + android.os.Build.VERSION.SDK_INT + "\n" +
                "Battery: " + getBatteryLevel() + "%";

        deviceInfo.setText(info);

        btnBack.setOnClickListener(v -> finish());
    }

    private int getBatteryLevel() {
        android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
}
