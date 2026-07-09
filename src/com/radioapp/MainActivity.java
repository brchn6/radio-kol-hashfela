package com.radioapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

public class MainActivity extends Activity {

    private static final String[] HASHFELA_BACKGROUND_URLS = {
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/TEL_AZEKA_A.jpg/1280px-TEL_AZEKA_A.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/7/76/Adullam-France_Park.jpg/1280px-Adullam-France_Park.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Pristine_wilderness_in_Adullam-France_Park.jpg/1280px-Pristine_wilderness_in_Adullam-France_Park.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/Park_Britannia_DSC_1202_%288411816956%29.jpg/1280px-Park_Britannia_DSC_1202_%288411816956%29.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/5/54/The_Elah_valley.jpg/1280px-The_Elah_valley.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Valley_of_Elah.jpg/1280px-Valley_of_Elah.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Israel_Beit_Guvrin_P1050959.JPG/1280px-Israel_Beit_Guvrin_P1050959.JPG",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/135175_eshtaol_forest_observation_PikiWiki_Israel.jpg/1280px-135175_eshtaol_forest_observation_PikiWiki_Israel.jpg"
    };

    private Button toggleButton;
    private TextView statusText;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ─── Background ImageView ────────────────────────────────────
        ImageView backgroundImage = new ImageView(this);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setColorFilter(Color.argb(100, 0, 0, 0)); // dim overlay

        // ─── Title ───────────────────────────────────────────────────
        TextView title = new TextView(this);
        title.setText(R.string.radio_name);
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setShadowLayer(8, 0, 2, Color.BLACK);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(24, 0, 24, 12);

        // ─── Status ──────────────────────────────────────────────────
        statusText = new TextView(this);
        statusText.setText("Loading…");
        statusText.setTextSize(18);
        statusText.setTextColor(Color.argb(220, 255, 255, 255));
        statusText.setShadowLayer(6, 0, 2, Color.BLACK);
        statusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        statusText.setPadding(24, 0, 24, 40);

        // ─── Play / Stop Button ─────────────────────────────────────
        toggleButton = new Button(this, null, android.R.attr.buttonStyle);
        toggleButton.setText(getString(R.string.play));
        toggleButton.setTextSize(22);
        toggleButton.setTypeface(null, Typeface.BOLD);
        toggleButton.setPadding(72, 20, 72, 20);
        toggleButton.setBackgroundColor(Color.argb(210, 255, 255, 255));
        toggleButton.setTextColor(Color.BLACK);
        toggleButton.setAllCaps(false);
        toggleButton.setOnClickListener(v -> togglePlayback());

        // ─── WhatsApp Button ────────────────────────────────────────
        Button whatsappButton = new Button(this, null, android.R.attr.buttonStyle);
        whatsappButton.setText(getString(R.string.whatsapp_button));
        whatsappButton.setTextSize(16);
        whatsappButton.setPadding(42, 14, 42, 14);
        whatsappButton.setBackgroundColor(Color.argb(190, 37, 211, 102));
        whatsappButton.setTextColor(Color.WHITE);
        whatsappButton.setAllCaps(false);
        whatsappButton.setOnClickListener(v -> openWhatsApp());

        // ─── Overlay: title + status + button, stacked vertically ───
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.addView(title);
        overlay.addView(statusText);
        overlay.addView(toggleButton);

        LinearLayout.LayoutParams whatsappParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        whatsappParams.topMargin = 28;
        overlay.addView(whatsappButton, whatsappParams);

        // ─── Root: FrameLayout stacks image under overlay ───────────
        FrameLayout root = new FrameLayout(this);
        root.addView(backgroundImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setContentView(root);

        // ─── Fetch a random Hashfela nature photo in the background ─
        new Thread(() -> {
            Bitmap bmp = downloadImage(randomHashfelaBackgroundUrl());
            if (bmp != null) {
                runOnUiThread(() -> backgroundImage.setImageBitmap(bmp));
            }
        }).start();

        requestNotificationPermission();

        // ─── Auto-start playback ────────────────────────────────────
        togglePlayback();
    }

    // ─── Toggle play / stop ────────────────────────────────────────────
    private void togglePlayback() {
        if (isPlaying) {
            startRadioService(RadioService.ACTION_STOP);
            toggleButton.setText(getString(R.string.play));
            statusText.setText("Stopped");
            isPlaying = false;
        } else {
            startRadioService(RadioService.ACTION_PLAY);
            toggleButton.setText(getString(R.string.stop));
            statusText.setText("Now Playing…");
            isPlaying = true;
        }
    }

    private void startRadioService(String action) {
        Intent intent = new Intent(this, RadioService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1036);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1036 && isPlaying) {
            startRadioService(RadioService.ACTION_PLAY);
        }
    }

    private void openWhatsApp() {
        String url = "https://wa.me/" + getString(R.string.whatsapp_phone)
                + "?text=" + Uri.encode(getString(R.string.whatsapp_message));
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private String randomHashfelaBackgroundUrl() {
        return HASHFELA_BACKGROUND_URLS[new Random().nextInt(HASHFELA_BACKGROUND_URLS.length)];
    }

    // ─── Download an image from a URL ─────────────────────────────────
    private Bitmap downloadImage(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "RadioKolHashfela/1.0");
            conn.connect();
            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                return bmp;
            }
        } catch (Exception ignored) {
            // fall through — background stays dimmed black, looks fine
        }
        return null;
    }
}
