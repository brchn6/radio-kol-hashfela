package com.radioapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RadioService extends Service {

    private static final String CHANNEL_ID = "radio_channel";
    private static final int NOTIF_ID = 1001;
    private static final String TAG = "RadioService";

    public static final String ACTION_PLAY = "com.radioapp.PLAY";
    public static final String ACTION_STOP = "com.radioapp.STOP";
    public static final String ACTION_METADATA_UPDATE = "com.radioapp.METADATA_UPDATE";
    public static final String EXTRA_METADATA = "metadata";

    private ExoPlayer player;
    private MediaSession mediaSession;
    private boolean metadataFetchInProgress = false;
    private String currentMetadata = "";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable metadataRunnable = new Runnable() {
        @Override
        public void run() {
            boolean shouldFetch;
            synchronized (this) {
                shouldFetch = isActuallyPlaying() && !metadataFetchInProgress;
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
                        synchronized (this) {
                            metadataFetchInProgress = false;
                        }
                    }
                }, "IcyMetadataReader").start();
            }

            handler.postDelayed(this, 30000);
        }
    };

    private boolean isActuallyPlaying() {
        return player != null && player.getPlaybackState() == Player.STATE_READY && player.getPlayWhenReady();
    }

    // ─── Player listener ──────────────────────────────────────────────────

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            switch (playbackState) {
                case Player.STATE_READY:
                    Log.i(TAG, "ExoPlayer ready — streaming");
                    updateNotification(currentMetadata.isEmpty() ? "Now Playing" : currentMetadata, true);
                    updatePlaybackState(true);
                    handler.removeCallbacks(metadataRunnable);
                    handler.post(metadataRunnable);
                    break;
                case Player.STATE_BUFFERING:
                    Log.i(TAG, "ExoPlayer buffering");
                    updateNotification("Buffering…", true);
                    break;
                case Player.STATE_ENDED:
                    Log.w(TAG, "Stream ended unexpectedly");
                    handleUnexpectedStop();
                    break;
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.e(TAG, "ExoPlayer error: " + error.getErrorCodeName() + " — " + error.getMessage());
            handleUnexpectedStop();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                Log.i(TAG, "ExoPlayer is playing");
            }
        }
    };

    // ─── Lifecycle ────────────────────────────────────────────────────────

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

        startPlayback();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(metadataRunnable);
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
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

    // ─── Playback ─────────────────────────────────────────────────────────

    private void startPlayback() {
        if (player != null) {
            if (isActuallyPlaying()) return;
            // Reuse existing player — just start it
            player.prepare();
            player.play();
            return;
        }

        Log.i(TAG, "Starting ExoPlayer");

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("RadioKolHashfela/1.0")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
                .build();

        player.addListener(playerListener);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(getString(R.string.radio_url))));
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private void stopPlaybackByUser() {
        Log.i(TAG, "Stopping playback by user request");
        handler.removeCallbacks(metadataRunnable);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        currentMetadata = "";
        updateNotification("Stopped", false);
        updatePlaybackState(false);
    }

    private void handleUnexpectedStop() {
        // ExoPlayer handles its own reconnection internally.
        // If we got here, something serious happened — try again.
        Log.i(TAG, "Handling unexpected stop, will retry");
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        updateNotification("Reconnecting…", true);
        updatePlaybackState(false);
        handler.postDelayed(() -> {
            if (player != null) {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(getString(R.string.radio_url))));
                player.prepare();
                player.setPlayWhenReady(true);
            } else {
                startPlayback();
            }
        }, 1000);
    }

    // ─── Metadata ─────────────────────────────────────────────────────────

    private void updateMetadata(String metadata) {
        if (isGenericMetadata(metadata)) {
            Log.i(TAG, "Ignoring generic stream metadata: " + metadata);
            return;
        }
        currentMetadata = metadata;
        Log.i(TAG, "Stream metadata: " + metadata);
        broadcastMetadata(metadata);
        updateNotification(metadata, true);
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

    // ─── MediaSession ─────────────────────────────────────────────────────

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "RadioKolHashfela");
        mediaSession.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
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

    // ─── Notification ─────────────────────────────────────────────────────

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
