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
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

public class RadioService extends Service {

    private static final String CHANNEL_ID = "radio_channel";
    private static final int NOTIF_ID = 1001;
    private static final String TAG = "RadioService";

    public static final String ACTION_PLAY = "com.radioapp.PLAY";
    public static final String ACTION_STOP = "com.radioapp.STOP";

    private MediaPlayer mediaPlayer;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        createMediaSession();
        startForeground(NOTIF_ID, buildNotification("Connecting\u2026", true));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            updateNotification("Stopped", false);
            return START_NOT_STICKY;
        }

        startPlayback();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        stopPlayback();
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
                startPlayback();
            }

            @Override
            public void onPause() {
                stopPlayback();
                updateNotification("Stopped", false);
            }

            @Override
            public void onStop() {
                stopPlayback();
                updateNotification("Stopped", false);
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
        if (mediaPlayer != null) {
            updateNotification("Now Playing", true);
            updatePlaybackState(true);
            return;
        }

        updateNotification("Connecting\u2026", true);

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());

        try {
            mediaPlayer.setDataSource(getString(R.string.radio_url));
            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                updateNotification("Now Playing", true);
                updatePlaybackState(true);
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
                stopPlayback();
                updateNotification("Error playing", false);
                updatePlaybackState(false);
                return true;
            });

            mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    updateNotification("Buffering\u2026", true);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    updateNotification("Now Playing", true);
                }
                return false;
            });

        } catch (IOException e) {
            Log.e(TAG, "Failed to set data source", e);
            stopPlayback();
            updateNotification("Error: " + e.getMessage(), false);
            updatePlaybackState(false);
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
                // Player may already be stopped after an error.
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        updatePlaybackState(false);
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
