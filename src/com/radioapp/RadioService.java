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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;

public class RadioService extends Service {

    private static final String CHANNEL_ID = "radio_channel";
    private static final int NOTIF_ID = 1001;
    private static final String TAG = "RadioService";

    private static final long BASE_RECONNECT_DELAY_MS = 2000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;

    public static final String ACTION_PLAY = "com.radioapp.PLAY";
    public static final String ACTION_STOP = "com.radioapp.STOP";

    private final Object playerLock = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private MediaSession mediaSession;
    private boolean shouldKeepPlaying = false;
    private boolean isPreparing = false;
    private boolean isPlaying = false;
    private int reconnectAttempt = 0;

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
            updateNotification("Now Playing", true);
            updatePlaybackState(true);
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
        synchronized (playerLock) {
            shouldKeepPlaying = false;
            reconnectAttempt = 0;
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
