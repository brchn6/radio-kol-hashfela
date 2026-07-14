package com.radioapp;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "radio_prefs";
    private static final String PREF_TRACK_HISTORY = "track_history_json";

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
    private TextView playlistText;
    private final ArrayList<String> trackHistory = new ArrayList<>();
    private boolean isPlaying = false;

    private final BroadcastReceiver metadataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RadioService.ACTION_METADATA_UPDATE.equals(intent.getAction()) && isPlaying) {
                String metadata = intent.getStringExtra(RadioService.EXTRA_METADATA);
                if (metadata != null && !metadata.trim().isEmpty()) {
                    statusText.setText(metadata);
                    loadTrackHistory();
                    if (isRealTrackName(metadata)) {
                        addTrackToHistory(metadata.trim());
                    }
                    updatePlaylistText();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ─── Background ImageView ────────────────────────────────────
        ImageView backgroundImage = new ImageView(this);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setColorFilter(Color.argb(100, 0, 0, 0));

        // ─── Title ───────────────────────────────────────────────────
        TextView title = new TextView(this);
        title.setText(R.string.radio_name);
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setShadowLayer(8, 0, 2, Color.BLACK);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(24, 0, 24, 8);

        // ─── Status ──────────────────────────────────────────────────
        statusText = new TextView(this);
        statusText.setText("Loading\u2026");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.argb(220, 255, 255, 255));
        statusText.setShadowLayer(6, 0, 2, Color.BLACK);
        statusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        statusText.setPadding(24, 0, 24, 24);

        // ─── Last tracks / playlist history ──────────────────────────
        playlistText = new TextView(this);
        playlistText.setTextSize(13);
        playlistText.setTextColor(Color.argb(215, 255, 255, 255));
        playlistText.setShadowLayer(6, 0, 2, Color.BLACK);
        playlistText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        playlistText.setPadding(32, 0, 32, 0);
        playlistText.setMaxLines(6);
        loadTrackHistory();
        updatePlaylistText();

        // ─── Circular Play / Pause Button ────────────────────────────
        toggleButton = new Button(this, null, android.R.attr.buttonStyle);
        toggleButton.setText("\u25B6");  // ▶ play triangle
        toggleButton.setTextSize(36);
        toggleButton.setTypeface(Typeface.DEFAULT);
        toggleButton.setTextColor(Color.BLACK);
        toggleButton.setAllCaps(false);

        // Make it a circle
        int btnSize = dpToPx(76);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setSize(btnSize, btnSize);
        circleBg.setColor(Color.argb(220, 255, 255, 255));
        toggleButton.setBackground(circleBg);
        toggleButton.setMinWidth(btnSize);
        toggleButton.setMinHeight(btnSize);
        toggleButton.setPadding(0, 0, 0, 0);
        toggleButton.setOnClickListener(v -> togglePlayback());

        // ─── Center column: title + status + play button ─────────────
        FrameLayout centerColumn = new FrameLayout(this);
        centerColumn.setForegroundGravity(Gravity.CENTER);

        // Title at top of center area
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        titleParams.topMargin = dpToPx(32);
        centerColumn.addView(title, titleParams);

        // Status below title
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        statusParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        statusParams.topMargin = dpToPx(84);
        centerColumn.addView(statusText, statusParams);

        // Playlist history below current status
        FrameLayout.LayoutParams playlistParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        playlistParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        playlistParams.topMargin = dpToPx(132);
        centerColumn.addView(playlistText, playlistParams);

        // Play button centered
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                btnSize, btnSize);
        playParams.gravity = Gravity.CENTER;
        centerColumn.addView(toggleButton, playParams);

        // ─── WhatsApp Button — bottom-left ───────────────────────────
        Button whatsappButton = new Button(this, null, android.R.attr.buttonStyle);
        whatsappButton.setText("\uD83D\uDCAC  WhatsApp");
        whatsappButton.setTextSize(15);
        whatsappButton.setTypeface(Typeface.DEFAULT);
        whatsappButton.setTextColor(Color.WHITE);
        whatsappButton.setAllCaps(false);

        GradientDrawable waBg = new GradientDrawable();
        waBg.setShape(GradientDrawable.RECTANGLE);
        waBg.setCornerRadius(dpToPx(24));
        waBg.setColor(Color.parseColor("#25D366"));
        whatsappButton.setBackground(waBg);
        whatsappButton.setPadding(dpToPx(20), dpToPx(12), dpToPx(24), dpToPx(12));
        whatsappButton.setOnClickListener(v -> openWhatsApp());

        FrameLayout.LayoutParams waParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        waParams.gravity = Gravity.BOTTOM | Gravity.START;
        waParams.setMargins(dpToPx(20), 0, 0, dpToPx(40));
        whatsappButton.setLayoutParams(waParams);

        // ─── Copy Playlist Button — bottom-right ─────────────────────
        Button copyPlaylistButton = new Button(this, null, android.R.attr.buttonStyle);
        copyPlaylistButton.setText("\uD83D\uDCCB  Copy playlist");
        copyPlaylistButton.setTextSize(15);
        copyPlaylistButton.setTypeface(Typeface.DEFAULT);
        copyPlaylistButton.setTextColor(Color.WHITE);
        copyPlaylistButton.setAllCaps(false);

        GradientDrawable copyBg = new GradientDrawable();
        copyBg.setShape(GradientDrawable.RECTANGLE);
        copyBg.setCornerRadius(dpToPx(24));
        copyBg.setColor(Color.argb(220, 70, 100, 220));
        copyPlaylistButton.setBackground(copyBg);
        copyPlaylistButton.setPadding(dpToPx(20), dpToPx(12), dpToPx(24), dpToPx(12));
        copyPlaylistButton.setOnClickListener(v -> copyPlaylistToClipboard());

        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        copyParams.gravity = Gravity.BOTTOM | Gravity.END;
        copyParams.setMargins(0, 0, dpToPx(20), dpToPx(40));
        copyPlaylistButton.setLayoutParams(copyParams);

        // ─── Root: FrameLayout stacks everything ─────────────────────
        FrameLayout root = new FrameLayout(this);
        root.addView(backgroundImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Center column layered on top
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(centerColumn, centerParams);

        // Bottom actions layered on top
        root.addView(whatsappButton, waParams);
        root.addView(copyPlaylistButton, copyParams);

        setContentView(root);

        // ─── Fetch a random Hashfela nature photo in the background ──
        new Thread(() -> {
            Bitmap bmp = downloadImage(randomHashfelaBackgroundUrl());
            if (bmp != null) {
                runOnUiThread(() -> backgroundImage.setImageBitmap(bmp));
            }
        }).start();

        requestNotificationPermission();

        // ─── Auto-start playback ─────────────────────────────────────
        togglePlayback();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RadioService.ACTION_METADATA_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(metadataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(metadataReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(metadataReceiver);
        super.onStop();
    }

    // ─── Toggle play / stop ────────────────────────────────────────────────
    private void togglePlayback() {
        if (isPlaying) {
            startRadioService(RadioService.ACTION_STOP);
            toggleButton.setText("\u25B6");   // ▶
            statusText.setText("Stopped");
            isPlaying = false;
        } else {
            startRadioService(RadioService.ACTION_PLAY);
            toggleButton.setText("\u23F8");   // ⏸
            statusText.setText("Now Playing");
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
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open WhatsApp link", Toast.LENGTH_LONG).show();
        }
    }

    private void loadTrackHistory() {
        trackHistory.clear();
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            JSONArray saved = new JSONArray(prefs.getString(PREF_TRACK_HISTORY, "[]"));
            for (int i = 0; i < saved.length() && trackHistory.size() < 5; i++) {
                String track = saved.optString(i, "").trim();
                if (isRealTrackName(track) && !trackHistory.contains(track)) {
                    trackHistory.add(track);
                }
            }
        } catch (Exception ignored) {
            // Broken history should not affect playback.
        }
    }

    private void addTrackToHistory(String track) {
        if (!isRealTrackName(track)) return;
        trackHistory.remove(track);
        trackHistory.add(0, track);
        while (trackHistory.size() > 5) {
            trackHistory.remove(trackHistory.size() - 1);
        }
        saveTrackHistory();
    }

    private void saveTrackHistory() {
        JSONArray saved = new JSONArray();
        for (String track : trackHistory) {
            saved.put(track);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_TRACK_HISTORY, saved.toString())
                .apply();
    }

    private void updatePlaylistText() {
        if (playlistText == null) return;
        if (trackHistory.isEmpty()) {
            playlistText.setText("Last tracks will appear here");
            return;
        }

        StringBuilder text = new StringBuilder("Last tracks:\n");
        for (int i = 0; i < trackHistory.size(); i++) {
            text.append(i + 1).append(". ").append(trackHistory.get(i));
            if (i < trackHistory.size() - 1) text.append('\n');
        }
        playlistText.setText(text.toString());
    }

    private void copyPlaylistToClipboard() {
        loadTrackHistory();
        updatePlaylistText();
        if (trackHistory.isEmpty()) {
            Toast.makeText(this, "No track history yet", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < trackHistory.size(); i++) {
            text.append(i + 1).append(". ").append(trackHistory.get(i));
            if (i < trackHistory.size() - 1) text.append('\n');
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Radio Kol Hashfela playlist", text.toString()));
        Toast.makeText(this, "Playlist copied", Toast.LENGTH_SHORT).show();
    }

    private boolean isRealTrackName(String track) {
        if (track == null) return false;
        String normalized = track.trim().toLowerCase();
        return !normalized.isEmpty()
                && !"song not found".equals(normalized)
                && !"streaming powered by multix".equals(normalized)
                && !"radio kol hashfela".equals(normalized);
    }

    private String randomHashfelaBackgroundUrl() {
        return HASHFELA_BACKGROUND_URLS[new Random().nextInt(HASHFELA_BACKGROUND_URLS.length)];
    }

    // ─── Download an image from a URL ─────────────────────────────────────
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

    // ─── Density-independent pixel helper ─────────────────────────────────
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
