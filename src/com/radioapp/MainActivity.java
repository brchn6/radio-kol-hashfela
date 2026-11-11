package com.radioapp;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Single screen, modelled on the station's original app: orange background,
 * the 103.6FM logo on a white card, one big play button, then WhatsApp and
 * the station's social links. All playback state comes from RadioService.
 */
public class MainActivity extends Activity {

    private static final int WHITE = Color.WHITE;

    private ImageButton playButton;
    private ProgressBar connectingRing;
    private TextView statusText;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        requestNotificationPermission();

        // Open the app = start listening (unless it's already on).
        if (savedInstanceState == null && RadioService.getState() == RadioService.STATE_STOPPED) {
            play();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RadioService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        render();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(stateReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // Activity really finished (Back on Android 11 and older) = radio off too.
        // On Android 12+ Back only hides the app and the radio keeps playing;
        // swiping it from Recents stops it (RadioService.onTaskRemoved).
        if (isFinishing()) stop();
        super.onDestroy();
    }

    // ─── Playback control ─────────────────────────────────────────────────

    private void togglePlayback() {
        if (RadioService.getState() == RadioService.STATE_STOPPED) {
            play();
        } else {
            stop();
        }
    }

    private void play() {
        Intent intent = new Intent(this, RadioService.class).setAction(RadioService.ACTION_PLAY);
        startForegroundService(intent);
    }

    private void stop() {
        if (RadioService.getState() == RadioService.STATE_STOPPED) return;
        startService(new Intent(this, RadioService.class).setAction(RadioService.ACTION_STOP));
    }

    /** Draw the play button and status line from the service's real state. */
    private void render() {
        int state = RadioService.getState();
        String track = RadioService.getTrack();

        boolean stopped = state == RadioService.STATE_STOPPED;
        playButton.setImageResource(stopped ? R.drawable.ic_play : R.drawable.ic_pause);
        playButton.setContentDescription(getString(stopped ? R.string.play : R.string.stop));

        boolean waiting = state == RadioService.STATE_CONNECTING
                || state == RadioService.STATE_RECONNECTING;
        connectingRing.setVisibility(waiting ? View.VISIBLE : View.INVISIBLE);

        switch (state) {
            case RadioService.STATE_PLAYING:
                statusText.setText(track.isEmpty() ? "● " + getString(R.string.status_live) : track);
                break;
            case RadioService.STATE_CONNECTING:
                statusText.setText(R.string.status_connecting);
                break;
            case RadioService.STATE_RECONNECTING:
                statusText.setText(R.string.status_reconnecting);
                break;
            case RadioService.STATE_PAUSED:
                statusText.setText(R.string.status_paused);
                break;
            default:
                statusText.setText(R.string.status_stopped);
        }
    }

    // ─── Layout ───────────────────────────────────────────────────────────

    private View buildLayout() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float wDp = dm.widthPixels / dm.density;
        float hDp = dm.heightPixels / dm.density;
        int cardDp = (int) Math.min(Math.min(wDp * 0.62f, hDp * 0.30f), 320f);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        column.addView(new View(this), weighted());

        // Logo card
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.station_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(rounded(WHITE, dp(28)));
        logo.setClipToOutline(true);
        logo.setElevation(dp(10));
        logo.setContentDescription(getString(R.string.notif_title));
        column.addView(logo, sized(dp(cardDp), dp(cardDp), 0));

        // Name + frequency
        TextView title = text(getString(R.string.radio_name), 30, WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        column.addView(title, wrap(dp(22)));

        TextView subtitle = text(getString(R.string.radio_subtitle), 17, alpha(WHITE, 0.9f));
        column.addView(subtitle, wrap(dp(2)));

        // Status pill
        statusText = text("", 15, WHITE);
        statusText.setBackground(rounded(Color.argb(46, 0, 0, 0), dp(16)));
        statusText.setPadding(dp(14), dp(6), dp(14), dp(6));
        statusText.setMaxLines(2);
        column.addView(statusText, wrap(dp(14)));

        // Play button with a spinner ring while connecting
        int btn = dp(92);
        int ring = dp(112);
        FrameLayout playHolder = new FrameLayout(this);
        connectingRing = new ProgressBar(this);
        connectingRing.setIndeterminate(true);
        connectingRing.setIndeterminateTintList(ColorStateList.valueOf(WHITE));
        playHolder.addView(connectingRing, new FrameLayout.LayoutParams(ring, ring, Gravity.CENTER));

        playButton = new ImageButton(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(WHITE);
        playButton.setBackground(circle);
        playButton.setImageTintList(ColorStateList.valueOf(getColor(R.color.brand_orange)));
        playButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        playButton.setPadding(dp(24), dp(24), dp(24), dp(24));
        playButton.setElevation(dp(8));
        playButton.setOnClickListener(v -> togglePlayback());
        playHolder.addView(playButton, new FrameLayout.LayoutParams(btn, btn, Gravity.CENTER));
        column.addView(playHolder, sized(ring, ring, dp(18)));

        column.addView(new View(this), weighted());

        // WhatsApp to the station
        Button whatsapp = pill(getString(R.string.whatsapp_button), Color.parseColor("#25D366"), 17);
        whatsapp.setTypeface(Typeface.DEFAULT_BOLD);
        whatsapp.setOnClickListener(v -> openWhatsApp());
        LinearLayout.LayoutParams waParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        waParams.topMargin = dp(20);
        column.addView(whatsapp, waParams);

        // Station links
        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        addLink(links, R.string.link_facebook, R.string.url_facebook);
        addLink(links, R.string.link_instagram, R.string.url_instagram);
        addLink(links, R.string.link_youtube, R.string.url_youtube);
        addLink(links, R.string.link_website, R.string.url_website);
        LinearLayout.LayoutParams linkRow = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        linkRow.topMargin = dp(10);
        column.addView(links, linkRow);

        // Footer: honest about being a fan app
        TextView footer = text(getString(R.string.footer, versionName()), 12, alpha(WHITE, 0.75f));
        column.addView(footer, wrap(dp(16)));

        // Scrolls only on very short screens; otherwise the spacers center everything
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{getColor(R.color.brand_orange), getColor(R.color.brand_orange_dark)});
        scroll.setBackground(bg);

        applyEdgeToEdge(scroll, column);
        return scroll;
    }

    /**
     * Android 15 draws apps under the status and navigation bars; do it on
     * every version and pad the content by the bar sizes so nothing is hidden.
     */
    private void applyEdgeToEdge(View root, View content) {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        final int side = dp(24);
        final int vertical = dp(16);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(side + left, vertical + top, side + right, vertical + bottom);
            return insets;
        });
    }

    private void addLink(LinearLayout row, int labelRes, int urlRes) {
        Button b = pill(getString(labelRes), Color.argb(56, 255, 255, 255), 14);
        b.setOnClickListener(v -> openUrl(getString(urlRes)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMarginStart(dp(3));
        lp.setMarginEnd(dp(3));
        row.addView(b, lp);
    }

    // ─── Links ────────────────────────────────────────────────────────────

    private void openWhatsApp() {
        openUrl("https://wa.me/" + getString(R.string.whatsapp_phone)
                + "?text=" + Uri.encode(getString(R.string.whatsapp_message)));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1036);
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    // ─── Small view helpers ───────────────────────────────────────────────

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button pill(String label, int color, int sp) {
        Button b = new Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(sp);
        b.setTextColor(WHITE);
        b.setMaxLines(1);
        // Shrink instead of clipping when the phone uses a large font size
        b.setAutoSizeTextTypeUniformWithConfiguration(9, sp, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(rounded(color, dp(26)));
        return b;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static int alpha(int color, float a) {
        return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    private LinearLayout.LayoutParams wrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private LinearLayout.LayoutParams sized(int w, int h, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = topMargin;
        return lp;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
