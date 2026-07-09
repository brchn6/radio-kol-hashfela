package com.radioapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

public class RadioService extends Service {

    private static final String CHANNEL_ID = "radio_channel";
    private static final int NOTIF_ID = 1001;
    private static final String TAG = "RadioService";

    private MediaPlayer mediaPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Connecting…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if ("STOP".equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (mediaPlayer == null) {
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
                    updateNotification("Now Playing");
                });

                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
                    updateNotification("Error playing");
                    return true; // don't call OnCompletionListener
                });

                mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        updateNotification("Buffering…");
                    } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        updateNotification("Now Playing");
                    }
                    return false;
                });

            } catch (IOException e) {
                Log.e(TAG, "Failed to set data source", e);
                updateNotification("Error: " + e.getMessage());
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- Notification helpers ---

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

    private Notification buildNotification(String status) {
        // Build a simple ongoing notification
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Kol Hashfela")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(status));
    }
}
