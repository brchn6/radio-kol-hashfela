package com.radioapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
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

public class MainActivity extends Activity {

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

        // ─── Overlay: title + status + button, stacked vertically ───
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.addView(title);
        overlay.addView(statusText);
        overlay.addView(toggleButton);

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

        // ─── Fetch a random nature photo in the background ──────────
        new Thread(() -> {
            Bitmap bmp = downloadImage(
                    "https://picsum.photos/1080/1920");
            if (bmp != null) {
                runOnUiThread(() -> backgroundImage.setImageBitmap(bmp));
            }
        }).start();

        // ─── Auto-start playback ────────────────────────────────────
        togglePlayback();
    }

    // ─── Toggle play / stop ────────────────────────────────────────────
    private void togglePlayback() {
        if (isPlaying) {
            stopService(new Intent(this, RadioService.class));
            toggleButton.setText(getString(R.string.play));
            statusText.setText("Stopped");
            isPlaying = false;
        } else {
            Intent intent = new Intent(this, RadioService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            toggleButton.setText(getString(R.string.stop));
            statusText.setText("Now Playing…");
            isPlaying = true;
        }
    }

    // ─── Download an image from a URL ─────────────────────────────────
    private Bitmap downloadImage(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
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
