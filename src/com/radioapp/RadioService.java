package com.radioapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.metadata.icy.IcyInfo;

/**
 * Plays the stream in a foreground service so it keeps going with the screen
 * off. ExoPlayer owns audio focus: it pauses for calls and navigation prompts
 * and resumes afterwards, and stops when headphones are unplugged.
 *
 * The current state lives in static fields so the activity can read it on
 * start, and every change is broadcast (ACTION_STATE_CHANGED) inside the app.
 */
public class RadioService extends Service {

    private static final String TAG = "RadioService";
    private static final String CHANNEL_ID = "radio_channel";
    private static final int NOTIF_ID = 1001;
    private static final int MIN_RETRY_MS = 2000;
    private static final int MAX_RETRY_MS = 30000;

    public static final String ACTION_PLAY = "com.radioapp.PLAY";
    public static final String ACTION_STOP = "com.radioapp.STOP";
    public static final String ACTION_STATE_CHANGED = "com.radioapp.STATE_CHANGED";

    public static final int STATE_STOPPED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_PLAYING = 2;
    /** Paused by the system for a call or navigation prompt; resumes by itself. */
    public static final int STATE_PAUSED = 3;
    public static final int STATE_RECONNECTING = 4;

    private static volatile int sState = STATE_STOPPED;
    private static volatile String sTrack = "";

    public static int getState() {
        return sState;
    }

    /** ICY song title when the station sends one, otherwise "". */
    public static String getTrack() {
        return sTrack;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private MediaSession mediaSession;
    private Bitmap logo;
    private int retryDelayMs = MIN_RETRY_MS;
    private boolean stopping = false;

    private ConnectivityManager connectivity;
    private Network currentNetwork;

    private final Runnable reconnectRunnable = () -> {
        if (player == null) return;
        Log.i(TAG, "Reconnecting");
        player.setMediaItem(streamItem());
        player.prepare();
        player.play();
    };

    /**
     * Reconnect the moment a network appears instead of waiting out the retry
     * timer: after airplane mode / no signal, and on Wi-Fi <-> mobile data
     * switches, where the old connection dies silently.
     */
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    handler.post(() -> onNetworkAvailable(network));
                }
            };

    private void onNetworkAvailable(Network network) {
        boolean switched = currentNetwork != null && !network.equals(currentNetwork);
        currentNetwork = network;
        if (player == null || stopping) return;
        if (sState == STATE_RECONNECTING || (switched && sState != STATE_PAUSED)) {
            Log.i(TAG, "Network available (switched=" + switched + "), reconnecting now");
            retryDelayMs = MIN_RETRY_MS;
            handler.removeCallbacks(reconnectRunnable);
            if (sState != STATE_RECONNECTING) setState(STATE_RECONNECTING);
            reconnectRunnable.run();
        }
    }

    // ─── Player events ────────────────────────────────────────────────────

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY) {
                retryDelayMs = MIN_RETRY_MS;
                refreshPlayingState();
            } else if (playbackState == Player.STATE_BUFFERING) {
                if (sState != STATE_RECONNECTING) setState(STATE_CONNECTING);
            } else if (playbackState == Player.STATE_ENDED) {
                Log.w(TAG, "Stream ended unexpectedly");
                scheduleReconnect();
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.e(TAG, "Player error: " + error.getErrorCodeName(), error);
            scheduleReconnect();
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            // Another app took the audio for good, or headphones were unplugged:
            // stop fully, the same as the user pressing Stop.
            if (!playWhenReady
                    && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                    || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) {
                Log.i(TAG, "Stopping: focus lost or headphones unplugged (reason " + reason + ")");
                handler.post(RadioService.this::stopPlayback);
            }
        }

        @Override
        public void onPlaybackSuppressionReasonChanged(int reason) {
            refreshPlayingState();
        }

        @Override
        public void onMetadata(Metadata metadata) {
            for (int i = 0; i < metadata.length(); i++) {
                Metadata.Entry entry = metadata.get(i);
                if (entry instanceof IcyInfo) {
                    String title = ((IcyInfo) entry).title;
                    String track = isGenericTitle(title) ? "" : title.trim();
                    if (!track.equals(sTrack)) {
                        sTrack = track;
                        publish();
                    }
                }
            }
        }
    };

    private void refreshPlayingState() {
        if (player == null || player.getPlaybackState() != Player.STATE_READY) return;
        boolean suppressed = player.getPlaybackSuppressionReason()
                != Player.PLAYBACK_SUPPRESSION_REASON_NONE;
        setState(suppressed ? STATE_PAUSED : STATE_PLAYING);
    }

    private static boolean isGenericTitle(String title) {
        String t = title == null ? "" : title.trim().toLowerCase();
        return t.isEmpty()
                || t.equals("streaming powered by multix")
                || t.equals("radio kol hashfela")
                || t.equals("kol hashfela");
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        logo = BitmapFactory.decodeResource(getResources(), R.drawable.station_logo);
        createNotificationChannel();
        createMediaSession();
        connectivity = getSystemService(ConnectivityManager.class);
        connectivity.registerDefaultNetworkCallback(networkCallback);
        sState = STATE_CONNECTING;
        sTrack = "";
        startForeground(NOTIF_ID, buildNotification());
        broadcastState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // NOT_STICKY everywhere: a system restart with a null intent would call
        // startForeground from the background, which Android 12+ forbids.
        String action = intent != null ? intent.getAction() : null;
        if (intent == null || ACTION_STOP.equals(action)) {
            stopPlayback();
            return START_NOT_STICKY;
        }
        // Every start request must be answered with startForeground, also when
        // Play arrives right after Stop on the same (not yet destroyed) instance.
        startForeground(NOTIF_ID, buildNotification());
        startPlayback();
        return START_NOT_STICKY;
    }

    /** Swiping the app away from Recents stops the radio (Bar's choice since v1). */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopPlayback();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        try {
            connectivity.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
            // not registered
        }
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        sState = STATE_STOPPED;
        sTrack = "";
        broadcastState();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Playback ─────────────────────────────────────────────────────────

    private MediaItem streamItem() {
        return MediaItem.fromUri(Uri.parse(getString(R.string.radio_url)));
    }

    private void startPlayback() {
        stopping = false;
        mediaSession.setActive(true);
        if (player != null) {
            if (!player.getPlayWhenReady() || player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
                player.play();
            }
            return;
        }

        Log.i(TAG, "Starting player");
        setState(STATE_CONNECTING);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("KolHashfelaApp/2.0")
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setAudioAttributes(attrs, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.addListener(playerListener);
        player.setMediaItem(streamItem());
        player.setPlayWhenReady(true);
        player.prepare();
    }

    /** Full stop: release the player, remove the notification, end the service. */
    private void stopPlayback() {
        if (stopping) return;
        stopping = true;
        Log.i(TAG, "Stopping playback");
        handler.removeCallbacks(reconnectRunnable);
        releasePlayer();
        sState = STATE_STOPPED;
        sTrack = "";
        updateSession();
        broadcastState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
    }

    /** Retry with backoff (2s, 4s, 8s ... 30s) so a dead network doesn't drain the battery. */
    private void scheduleReconnect() {
        if (player == null || stopping) return;
        setState(STATE_RECONNECTING);
        handler.removeCallbacks(reconnectRunnable);
        handler.postDelayed(reconnectRunnable, retryDelayMs);
        Log.i(TAG, "Retrying in " + retryDelayMs + " ms");
        retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_MS);
    }

    // ─── State publishing ─────────────────────────────────────────────────

    private void setState(int state) {
        if (stopping || sState == state) return;
        sState = state;
        publish();
    }

    private void publish() {
        if (stopping) return;
        updateSession();
        broadcastState();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification());
    }

    private void broadcastState() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private String statusText() {
        switch (sState) {
            case STATE_PLAYING:
                return sTrack.isEmpty() ? getString(R.string.status_live) : sTrack;
            case STATE_PAUSED:
                return getString(R.string.status_paused);
            case STATE_RECONNECTING:
                return getString(R.string.status_reconnecting);
            case STATE_CONNECTING:
                return getString(R.string.status_connecting);
            default:
                return getString(R.string.status_stopped);
        }
    }

    // ─── MediaSession (lock screen, Bluetooth, headset buttons) ───────────

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "KolHashfela");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                startPlayback();
            }

            @Override
            public void onPause() {
                stopPlayback();
            }

            @Override
            public void onStop() {
                stopPlayback();
            }
        });
        Intent open = new Intent(this, MainActivity.class);
        mediaSession.setSessionActivity(PendingIntent.getActivity(this, 0, open, pendingFlags()));
        mediaSession.setActive(true);
        updateSession();
    }

    private void updateSession() {
        if (mediaSession == null) return;
        int state;
        switch (sState) {
            case STATE_PLAYING:
                state = PlaybackState.STATE_PLAYING;
                break;
            case STATE_CONNECTING:
            case STATE_RECONNECTING:
                state = PlaybackState.STATE_BUFFERING;
                break;
            case STATE_PAUSED:
                state = PlaybackState.STATE_PAUSED;
                break;
            default:
                state = PlaybackState.STATE_STOPPED;
        }
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        state == PlaybackState.STATE_PLAYING ? 1f : 0f)
                .build());

        String stationTitle = getString(R.string.notif_title);
        mediaSession.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE,
                        sTrack.isEmpty() ? stationTitle : sTrack)
                .putString(MediaMetadata.METADATA_KEY_ARTIST,
                        sTrack.isEmpty() ? statusText() : stationTitle)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, logo)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, logo)
                .build());
    }

    // ─── Notification ─────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), pendingFlags());
        PendingIntent stop = PendingIntent.getService(
                this, 1, new Intent(this, RadioService.class).setAction(ACTION_STOP), pendingFlags());

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setShowActionsInCompactView(0)
                .setMediaSession(mediaSession.getSessionToken());

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(statusText())
                .setSmallIcon(R.drawable.ic_stat_radio)
                .setLargeIcon(logo)
                .setColor(getColor(R.color.brand_orange))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pause),
                        getString(R.string.stop), stop).build())
                .setStyle(style)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .build();
    }

    private int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
