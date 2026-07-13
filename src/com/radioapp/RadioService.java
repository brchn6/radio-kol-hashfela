package com.radioapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RadioService extends Service {

    private static final String CHANNEL_ID = "radio_channel";
    private static final int NOTIF_ID = 1001;
    private static final String TAG = "RadioService";

    private static final long BASE_RECONNECT_DELAY_MS = 2000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;
    private static final long AUTO_IDENTIFY_INITIAL_DELAY_MS = 20000;
    private static final long AUTO_IDENTIFY_INTERVAL_MS = 5 * 60 * 1000;
    private static final int AUDIOTAG_SAMPLE_BYTES = 128 * 1024;

    public static final String ACTION_PLAY = "com.radioapp.PLAY";
    public static final String ACTION_STOP = "com.radioapp.STOP";
    public static final String ACTION_METADATA_UPDATE = "com.radioapp.METADATA_UPDATE";
    public static final String EXTRA_METADATA = "metadata";

    private final Object playerLock = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private MediaSession mediaSession;
    private boolean shouldKeepPlaying = false;
    private boolean isPreparing = false;
    private boolean isPlaying = false;
    private boolean metadataFetchInProgress = false;
    private boolean audioTagInProgress = false;
    private int reconnectAttempt = 0;
    private String currentMetadata = "";
    private String lastIdentifiedTrack = "";

    private final Runnable reconnectRunnable = () -> {
        boolean shouldStart;
        synchronized (playerLock) {
            shouldStart = shouldKeepPlaying && mediaPlayer == null;
        }
        if (shouldStart) {
            Log.i(TAG, "Retrying radio stream");
            startPlayback();
        }
    };

    private final Runnable metadataRunnable = new Runnable() {
        @Override
        public void run() {
            boolean shouldFetch;
            synchronized (playerLock) {
                shouldFetch = shouldKeepPlaying && isPlaying && !metadataFetchInProgress;
                if (shouldFetch) metadataFetchInProgress = true;
            }

            if (shouldFetch) {
                new Thread(() -> {
                    try {
                        String metadata = fetchIcyMetadata();
                        if (metadata != null && !metadata.trim().isEmpty()) {
                            updateMetadata(metadata.trim());
                        }
                    } finally {
                        synchronized (playerLock) {
                            metadataFetchInProgress = false;
                        }
                    }
                }, "IcyMetadataReader").start();
            }

            synchronized (playerLock) {
                if (shouldKeepPlaying) {
                    handler.postDelayed(this, 30000);
                }
            }
        }
    };

    private final Runnable audioTagRunnable = new Runnable() {
        @Override
        public void run() {
            String apiKey = getString(R.string.audiotag_api_key).trim();
            boolean shouldIdentify;
            synchronized (playerLock) {
                shouldIdentify = shouldKeepPlaying && isPlaying && !audioTagInProgress && !apiKey.isEmpty();
                if (shouldIdentify) audioTagInProgress = true;
            }

            if (!shouldIdentify) {
                scheduleNextAudioTagRun(AUTO_IDENTIFY_INTERVAL_MS);
                return;
            }

            new Thread(() -> {
                try {
                    Log.i(TAG, "Auto AudioTag identification started");
                    updateNotification("Identifying song…", true);
                    File sample = captureStreamSampleForAudioTag();
                    String result = identifyWithAudioTag(sample, apiKey);
                    if (result != null && !result.trim().isEmpty()) {
                        updateRecognizedTrack(result.trim());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Auto AudioTag identification failed", e);
                } finally {
                    synchronized (playerLock) {
                        audioTagInProgress = false;
                    }
                    scheduleNextAudioTagRun(AUTO_IDENTIFY_INTERVAL_MS);
                }
            }, "AudioTagAutoIdentify").start();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        createMediaSession();
        startForeground(NOTIF_ID, buildNotification("Connecting…", true));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopPlaybackByUser();
            return START_NOT_STICKY;
        }

        synchronized (playerLock) {
            shouldKeepPlaying = true;
            reconnectAttempt = 0;
        }
        startPlayback();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(metadataRunnable);
        handler.removeCallbacks(audioTagRunnable);
        synchronized (playerLock) {
            shouldKeepPlaying = false;
        }
        releaseCurrentPlayer();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── MediaSession ─────────────────────────────────────────────────────

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "RadioKolHashfela");
        mediaSession.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                synchronized (playerLock) {
                    shouldKeepPlaying = true;
                    reconnectAttempt = 0;
                }
                startPlayback();
            }

            @Override
            public void onPause() {
                stopPlaybackByUser();
            }

            @Override
            public void onStop() {
                stopPlaybackByUser();
            }
        });
        mediaSession.setActive(true);
        updatePlaybackState(false);
    }

    private void updatePlaybackState(boolean playing) {
        if (mediaSession == null) return;
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_STOP;
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, playing ? 1.0f : 0f)
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    // ─── MediaPlayer ──────────────────────────────────────────────────────

    private void startPlayback() {
        MediaPlayer player;

        synchronized (playerLock) {
            shouldKeepPlaying = true;
            if (mediaPlayer != null) {
                updateNotification(isPlaying ? "Now Playing" : "Connecting…", true);
                updatePlaybackState(isPlaying);
                return;
            }

            player = new MediaPlayer();
            mediaPlayer = player;
            isPreparing = true;
            isPlaying = false;
        }

        handler.removeCallbacks(reconnectRunnable);
        updateNotification("Connecting…", true);
        updatePlaybackState(false);
        Log.i(TAG, "Starting radio stream");

        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());

        player.setOnPreparedListener(mp -> {
            boolean started = false;
            boolean stalePlayer = false;
            synchronized (playerLock) {
                if (mp != mediaPlayer || !shouldKeepPlaying) {
                    stalePlayer = true;
                } else {
                    isPreparing = false;
                    try {
                        mp.start();
                        isPlaying = true;
                        reconnectAttempt = 0;
                        started = true;
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Failed to start prepared player", e);
                        mediaPlayer = null;
                        isPreparing = false;
                        isPlaying = false;
                    }
                }
            }

            if (stalePlayer) {
                safeRelease(mp);
                return;
            }
            if (!started) {
                safeRelease(mp);
                scheduleReconnect("Error playing");
                return;
            }

            Log.i(TAG, "Radio stream is playing");
            updateNotification(currentMetadata.isEmpty() ? "Now Playing" : currentMetadata, true);
            updatePlaybackState(true);
            scheduleMetadataUpdates();
            scheduleNextAudioTagRun(AUTO_IDENTIFY_INITIAL_DELAY_MS);
        });

        player.setOnErrorListener((mp, what, extra) -> {
            synchronized (playerLock) {
                if (mp != mediaPlayer) return true;
            }
            Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
            handleUnexpectedStop(mp, "Stream interrupted");
            return true;
        });

        player.setOnCompletionListener(mp -> {
            synchronized (playerLock) {
                if (mp != mediaPlayer) return;
            }
            Log.w(TAG, "Live stream completed unexpectedly");
            handleUnexpectedStop(mp, "Stream ended");
        });

        player.setOnInfoListener((mp, what, extra) -> {
            synchronized (playerLock) {
                if (mp != mediaPlayer) return true;
            }
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                Log.i(TAG, "Buffering started");
                updateNotification("Buffering…", true);
            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                Log.i(TAG, "Buffering ended");
                updateNotification("Now Playing", true);
            }
            return false;
        });

        try {
            player.setDataSource(getString(R.string.radio_url));
            player.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "Failed to prepare stream", e);
            boolean currentPlayer;
            synchronized (playerLock) {
                currentPlayer = player == mediaPlayer;
                if (currentPlayer) {
                    mediaPlayer = null;
                    isPreparing = false;
                    isPlaying = false;
                }
            }
            safeRelease(player);
            if (currentPlayer) {
                scheduleReconnect("Connection failed");
            }
        }
    }

    private void stopPlaybackByUser() {
        Log.i(TAG, "Stopping playback by user request");
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(metadataRunnable);
        handler.removeCallbacks(audioTagRunnable);
        synchronized (playerLock) {
            shouldKeepPlaying = false;
            reconnectAttempt = 0;
            currentMetadata = "";
            lastIdentifiedTrack = "";
        }
        releaseCurrentPlayer();
        updateNotification("Stopped", false);
    }

    private void handleUnexpectedStop(MediaPlayer stoppedPlayer, String status) {
        boolean retry;
        synchronized (playerLock) {
            if (stoppedPlayer != mediaPlayer) return;
            retry = shouldKeepPlaying;
        }

        releaseCurrentPlayer();
        if (retry) {
            scheduleReconnect(status);
        } else {
            updateNotification("Stopped", false);
        }
    }

    private void scheduleReconnect(String reason) {
        long delayMs;
        synchronized (playerLock) {
            if (!shouldKeepPlaying) {
                updateNotification(reason, false);
                updatePlaybackState(false);
                return;
            }
            delayMs = Math.min(
                    BASE_RECONNECT_DELAY_MS * (1L << Math.min(reconnectAttempt, 4)),
                    MAX_RECONNECT_DELAY_MS);
            reconnectAttempt++;
        }

        long delaySeconds = Math.max(1, delayMs / 1000);
        Log.w(TAG, reason + "; reconnecting in " + delaySeconds + "s");
        updateNotification(reason + " — reconnecting in " + delaySeconds + "s", true);
        updatePlaybackState(false);
        handler.removeCallbacks(reconnectRunnable);
        handler.postDelayed(reconnectRunnable, delayMs);
    }

    private void scheduleMetadataUpdates() {
        handler.removeCallbacks(metadataRunnable);
        handler.post(metadataRunnable);
    }

    private void updateMetadata(String metadata) {
        synchronized (playerLock) {
            if (isGenericMetadata(metadata) && !lastIdentifiedTrack.isEmpty()) {
                return;
            }
            currentMetadata = metadata;
        }
        Log.i(TAG, "Stream metadata: " + metadata);
        broadcastMetadata(metadata);
        updateNotification(metadata, true);
    }

    private void updateRecognizedTrack(String track) {
        synchronized (playerLock) {
            lastIdentifiedTrack = track;
            currentMetadata = track;
        }
        Log.i(TAG, "AudioTag recognized: " + track);
        broadcastMetadata(track);
        updateNotification(track, true);
    }

    private void broadcastMetadata(String metadata) {
        Intent intent = new Intent(ACTION_METADATA_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_METADATA, metadata);
        sendBroadcast(intent);
    }

    private boolean isGenericMetadata(String metadata) {
        String normalized = metadata == null ? "" : metadata.trim().toLowerCase();
        return normalized.isEmpty()
                || "streaming powered by multix".equals(normalized)
                || "radio kol hashfela".equals(normalized);
    }

    private String fetchIcyMetadata() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(getString(R.string.radio_url));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Icy-MetaData", "1");
            conn.setRequestProperty("User-Agent", "RadioKolHashfela/1.0");
            conn.connect();

            String metaintHeader = conn.getHeaderField("icy-metaint");
            if (metaintHeader == null) return null;
            int metaint = Integer.parseInt(metaintHeader.trim());
            if (metaint <= 0) return null;

            InputStream input = conn.getInputStream();
            skipFully(input, metaint);
            int metadataBlocks = input.read();
            if (metadataBlocks <= 0) return null;

            int metadataLength = metadataBlocks * 16;
            ByteArrayOutputStream out = new ByteArrayOutputStream(metadataLength);
            byte[] buffer = new byte[metadataLength];
            int total = 0;
            while (total < metadataLength) {
                int read = input.read(buffer, total, metadataLength - total);
                if (read < 0) break;
                total += read;
            }
            out.write(buffer, 0, total);
            return parseStreamTitle(out.toString("UTF-8"));
        } catch (Exception e) {
            Log.w(TAG, "Failed to read stream metadata", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void skipFully(InputStream input, int bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() == -1) throw new IOException("End of stream while skipping audio");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private String parseStreamTitle(String metadata) {
        String marker = "StreamTitle='";
        int start = metadata.indexOf(marker);
        if (start < 0) return metadata.replace("\u0000", "").trim();
        start += marker.length();
        int end = metadata.indexOf("';", start);
        if (end < 0) end = metadata.length();
        return metadata.substring(start, end).replace("\u0000", "").trim();
    }

    private void scheduleNextAudioTagRun(long delayMs) {
        synchronized (playerLock) {
            if (!shouldKeepPlaying) return;
        }
        handler.removeCallbacks(audioTagRunnable);
        handler.postDelayed(audioTagRunnable, delayMs);
    }

    private File captureStreamSampleForAudioTag() throws Exception {
        File sample = new File(getCacheDir(), "audiotag-sample.aac");
        URL url = new URL(getString(R.string.radio_url));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "RadioKolHashfela/1.0");
        conn.connect();

        byte[] buffer = new byte[8192];
        int total = 0;
        try (InputStream input = conn.getInputStream();
             FileOutputStream output = new FileOutputStream(sample)) {
            while (total < AUDIOTAG_SAMPLE_BYTES) {
                int read = input.read(buffer, 0, Math.min(buffer.length, AUDIOTAG_SAMPLE_BYTES - total));
                if (read < 0) break;
                output.write(buffer, 0, read);
                total += read;
            }
        } finally {
            conn.disconnect();
        }

        if (total < 48_000) {
            throw new IOException("Could not capture enough audio for AudioTag");
        }
        return sample;
    }

    private String identifyWithAudioTag(File sample, String apiKey) throws Exception {
        JSONObject start = postAudioTagIdentify(sample, apiKey);
        if (!start.optBoolean("success", false)) {
            throw new IOException(start.optString("error", "AudioTag identify failed"));
        }

        String token = start.optString("token", "");
        if (token.isEmpty()) {
            throw new IOException("AudioTag did not return a token");
        }

        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            JSONObject result = postAudioTagResult(token, apiKey);
            if (!result.optBoolean("success", false)) {
                throw new IOException(result.optString("error", "AudioTag result failed"));
            }

            String state = result.optString("result", "wait");
            if ("wait".equals(state)) continue;
            if ("not found".equals(state)) return "Song not found";
            if ("found".equals(state)) return parseAudioTagResult(result);
            return "AudioTag: " + state;
        }

        return "AudioTag timed out";
    }

    private JSONObject postAudioTagIdentify(File sample, String apiKey) throws Exception {
        String boundary = "----RadioKolHashfela" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL("https://audiotag.info/api").openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", "RadioKolHashfela/1.0");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            writeMultipartField(out, boundary, "action", "identify");
            writeMultipartField(out, boundary, "apikey", apiKey);
            writeMultipartField(out, boundary, "time_len", "20");
            writeMultipartFile(out, boundary, "file", "radio-sample.aac", sample);
            out.writeBytes("--" + boundary + "--\r\n");
        }

        return readJsonResponse(conn);
    }

    private JSONObject postAudioTagResult(String token, String apiKey) throws Exception {
        String body = "action=get_result&apikey=" + Uri.encode(apiKey) + "&token=" + Uri.encode(token);
        HttpURLConnection conn = (HttpURLConnection) new URL("https://audiotag.info/api").openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", "RadioKolHashfela/1.0");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream output = conn.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readJsonResponse(conn);
    }

    private void writeMultipartField(DataOutputStream out, String boundary, String name, String value) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private void writeMultipartFile(DataOutputStream out, String boundary, String name, String filename, File file) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
        out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        out.writeBytes("\r\n");
    }

    private JSONObject readJsonResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream input = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        conn.disconnect();
        return new JSONObject(out.toString("UTF-8"));
    }

    private String parseAudioTagResult(JSONObject result) throws Exception {
        JSONArray data = result.optJSONArray("data");
        if (data == null || data.length() == 0) return "Song not found";

        JSONObject best = data.getJSONObject(0);
        JSONArray tracks = best.optJSONArray("tracks");
        if (tracks == null || tracks.length() == 0) return "Song not found";

        JSONArray track = tracks.getJSONArray(0);
        String title = track.optString(0, "");
        String artist = track.optString(1, "");
        if (title.isEmpty() && artist.isEmpty()) return "Song not found";
        if (artist.isEmpty()) return title;
        if (title.isEmpty()) return artist;
        return artist + " — " + title;
    }

    private void releaseCurrentPlayer() {
        MediaPlayer playerToRelease;
        synchronized (playerLock) {
            playerToRelease = mediaPlayer;
            mediaPlayer = null;
            isPreparing = false;
            isPlaying = false;
        }

        safeRelease(playerToRelease);
        updatePlaybackState(false);
    }

    private void safeRelease(MediaPlayer player) {
        if (player == null) return;
        player.setOnPreparedListener(null);
        player.setOnErrorListener(null);
        player.setOnCompletionListener(null);
        player.setOnInfoListener(null);
        try {
            if (player.isPlaying()) player.stop();
        } catch (IllegalStateException ignored) {
            // Player may be preparing, stopped, or already released after an error.
        }
        player.release();
    }

    // ─── Notification helpers ─────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Radio Player",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Radio playback notification");
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String status, boolean showStopAction) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, pendingIntentFlags());

        Intent actionIntent = new Intent(this, RadioService.class);
        actionIntent.setAction(showStopAction ? ACTION_STOP : ACTION_PLAY);
        PendingIntent actionPendingIntent = PendingIntent.getService(
                this, showStopAction ? 1 : 2, actionIntent, pendingIntentFlags());

        String actionLabel = showStopAction ? getString(R.string.stop) : getString(R.string.play);
        int actionIcon = showStopAction
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;

        Notification.MediaStyle mediaStyle = new Notification.MediaStyle()
                .setShowActionsInCompactView(0);
        if (mediaSession != null) {
            mediaStyle.setMediaSession(mediaSession.getSessionToken());
        }

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Kol Hashfela 103.6FM")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openPendingIntent)
                .addAction(actionIcon, actionLabel, actionPendingIntent)
                .setStyle(mediaStyle)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void updateNotification(String status, boolean showStopAction) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(status, showStopAction));
    }
}
