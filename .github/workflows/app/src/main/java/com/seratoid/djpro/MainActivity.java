package com.seratoid.djpro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import java.util.Locale;

public class MainActivity extends Activity {
    private final int BG_MAIN = Color.rgb(8, 11, 16);
    private final int BG_PANEL = Color.rgb(16, 21, 30);
    private final int BG_DARK = Color.rgb(22, 28, 40);
    private final int COLOR_DECK_A = Color.rgb(0, 229, 255);   // Serato Cyan
    private final int COLOR_DECK_B = Color.rgb(255, 109, 0);   // Serato Orange
    private final int[] HOT_CUE_COLORS = {
        Color.rgb(255, 45, 85),   // Pad 1: Red
        Color.rgb(255, 204, 0),   // Pad 2: Yellow
        Color.rgb(52, 199, 89),   // Pad 3: Green
        Color.rgb(175, 82, 222)   // Pad 4: Purple
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Deck deckA, deckB;
    private SeekBar crossfader;

    class Deck {
        final String label;
        final int themeColor;
        final int requestCode;

        MediaPlayer player;
        int[] hotCues = {-1, -1, -1, -1};
        float channelGain = 0.85f;
        float crossGain = 1.0f;
        float pitchRate = 1.0f;
        boolean stemsVocals = true, stemsMelody = true, stemsBass = true, stemsDrums = true;

        TextView titleView, timeView, bpmView, pitchLabel;
        Button playBtn, cueBtn;
        Button[] cuePads = new Button[4];
        Button[] stemButtons = new Button[4];
        SeekBar progressSeek, pitchSeek, gainSeek;
        TriColorWaveView waveView;
        JogWheelView jogWheel;

        Deck(String l, int color, int code) {
            label = l; themeColor = color; requestCode = code;
        }

        void load(Uri uri) {
            try {
                release();
                player = new MediaPlayer();
                player.setDataSource(MainActivity.this, uri);
                titleView.setText("Loading Deck " + label + "…");
                player.setOnPreparedListener(mp -> {
                    titleView.setText("Track " + label + " • Ready");
                    progressSeek.setMax(Math.max(1, mp.getDuration()));
                    timeView.setText("00:00 / " + fmt(mp.getDuration()));
                    bpmView.setText("128.00");
                    applyVolume();
                    applyPitch();
                });
                player.setOnCompletionListener(mp -> {
                    playBtn.setText("▶");
                    playBtn.setBackground(round(BG_DARK, 8));
                });
                player.prepareAsync();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Failed to load audio", Toast.LENGTH_SHORT).show();
            }
        }

        void togglePlay() {
            if (player == null) { pickAudio(requestCode); return; }
            if (player.isPlaying()) {
                player.pause();
                playBtn.setText("▶");
                playBtn.setBackground(round(BG_DARK, 8));
            } else {
                player.start();
                playBtn.setText("❚❚");
                playBtn.setBackground(round(themeColor, 8));
            }
        }

        void handleCue() {
            if (player == null) return;
            try {
                if (player.isPlaying()) {
                    player.pause();
                    player.seekTo(hotCues[0] >= 0 ? hotCues[0] : 0);
                    playBtn.setText("▶");
                    playBtn.setBackground(round(BG_DARK, 8));
                } else {
                    hotCues[0] = player.getCurrentPosition();
                    cuePads[0].setTextColor(Color.WHITE);
                    Toast.makeText(MainActivity.this, "CUE 1: " + fmt(hotCues[0]), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        }

        void triggerHotCue(int index) {
            if (player == null) return;
            try {
                if (hotCues[index] == -1) {
                    hotCues[index] = player.getCurrentPosition();
                    cuePads[index].setBackground(round(HOT_CUE_COLORS[index], 6));
                    cuePads[index].setTextColor(Color.BLACK);
                } else {
                    player.seekTo(hotCues[index]);
                    if (!player.isPlaying()) togglePlay();
                }
            } catch (Exception ignored) {}
        }

        void applyPitch() {
            if (player == null) return;
            try {
                PlaybackParams p = player.getPlaybackParams();
                p.setSpeed(pitchRate);
                player.setPlaybackParams(p);
            } catch (Exception ignored) {}
        }

        void applyVolume() {
            if (player != null) {
                float v = channelGain * crossGain;
                player.setVolume(v, v);
            }
        }

        void release() {
            if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
        }
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            tick(deckA);
            tick(deckB);
            handler.postDelayed(this, 80);
        }
    };

    private void tick(Deck d) {
        if (d.player == null) return;
        try {
            if (d.player.isPlaying()) {
                int pos = d.player.getCurrentPosition();
                d.progressSeek.setProgress(pos);
                d.timeView.setText(fmt(pos) + " / " + fmt(d.player.getDuration()));
                d.waveView.setProgress((float) pos / Math.max(1, d.player.getDuration()));
                d.jogWheel.rotate(d.pitchRate * 4f);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG_MAIN);
        getWindow().setNavigationBarColor(BG_MAIN);

        deckA = new Deck("A", COLOR_DECK_A, 101);
        deckB = new Deck("B", COLOR_DECK_B, 102);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(12), dp(10), dp(24));
        root.setBackgroundColor(BG_MAIN);
        scroll.addView(root);
        setContentView(scroll);

        // Header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("SERATOID", 17, Color.WHITE, true);
        TextView badge = text(" PRO ", 9, Color.BLACK, true);
        badge.setBackground(round(COLOR_DECK_A, 4));
        badge.setPadding(dp(4), dp(2), dp(4), dp(2));
        head.addView(brand);
        head.addView(badge, gap(6, 0, 0, 0));
        root.addView(head);

        TextView sub = text("PRO DUAL-DECK SYSTEM • v0.0.1", 10, Color.GRAY, false);
        root.addView(sub, gap(0, 2, 0, 10));

        // Deck A & Deck B panels
        root.addView(buildDeckUi(deckA));
        root.addView(buildDeckUi(deckB), gap(0, 10, 0, 10));

        // Master Crossfader
        LinearLayout mixPanel = panel();
        LinearLayout mixLabel = new LinearLayout(this);
        mixLabel.addView(text("DECK A", 10, COLOR_DECK_A, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView cfText = text("CROSSFADER", 9, Color.LTGRAY, true);
        cfText.setGravity(Gravity.CENTER);
        mixLabel.addView(cfText, new LinearLayout.LayoutParams(0, -2, 2));
        TextView rightLabel = text("DECK B", 10, COLOR_DECK_B, true);
        rightLabel.setGravity(Gravity.END);
        mixLabel.addView(rightLabel, new LinearLayout.LayoutParams(0, -2, 1));
        mixPanel.addView(mixLabel);

        crossfader = new SeekBar(this);
        crossfader.setMax(100);
        crossfader.setProgress(50);
        crossfader.setProgressTintList(ColorStateList.valueOf(COLOR_DECK_A));
        crossfader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                deckA.crossGain = (float) Math.cos((p / 100.0) * Math.PI / 2);
                deckB.crossGain = (float) Math.sin((p / 100.0) * Math.PI / 2);
                deckA.applyVolume();
                deckB.applyVolume();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        mixPanel.addView(crossfader, gap(0, 6, 0, 0));
        root.addView(mixPanel);

        handler.post(loop);
    }

    private View buildDeckUi(Deck d) {
        LinearLayout panel = panel();

        // Top info bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = text("DECK " + d.label, 11, d.themeColor, true);
        d.bpmView = text("---.--", 12, Color.WHITE, true);
        d.bpmView.setGravity(Gravity.END);
        top.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(d.bpmView);
        panel.addView(top);

        d.titleView = text("Tap LOAD to choose audio", 12, Color.LTGRAY, true);
        panel.addView(d.titleView, gap(0, 3, 0, 6));

        // Tri-Color Frequency Waveform
        d.waveView = new TriColorWaveView(this, d.themeColor);
        panel.addView(d.waveView, new LinearLayout.LayoutParams(-1, dp(44)));

        // Jogwheel & Pitch Row
        LinearLayout centerSection = new LinearLayout(this);
        centerSection.setGravity(Gravity.CENTER_VERTICAL);

        d.jogWheel = new JogWheelView(this, d.themeColor);
        centerSection.addView(d.jogWheel, new LinearLayout.LayoutParams(dp(70), dp(70)));

        // Pitch & Gain controls
        LinearLayout faders = new LinearLayout(this);
        faders.setOrientation(LinearLayout.VERTICAL);

        d.pitchLabel = text("PITCH: 0.0%", 8, Color.GRAY, true);
        d.pitchSeek = new SeekBar(this);
        d.pitchSeek.setMax(160);
        d.pitchSeek.setProgress(80);
        d.pitchSeek.setProgressTintList(ColorStateList.valueOf(d.themeColor));
        d.pitchSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float shift = (p - 80) / 10.0f; // -8% to +8%
                d.pitchRate = 1.0f + (shift / 100.0f);
                d.pitchLabel.setText(String.format(Locale.US, "PITCH: %+.1f%%", shift));
                d.applyPitch();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        faders.addView(d.pitchLabel);
        faders.addView(d.pitchSeek);

        TextView gainLbl = text("LEVEL", 8, Color.GRAY, true);
        d.gainSeek = new SeekBar(this);
        d.gainSeek.setMax(100);
        d.gainSeek.setProgress(85);
        d.gainSeek.setProgressTintList(ColorStateList.valueOf(d.themeColor));
        d.gainSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                d.channelGain = p / 100f;
                d.applyVolume();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        faders.addView(gainLbl, gap(0, 4, 0, 0));
        faders.addView(d.gainSeek);

        centerSection.addView(faders, layoutMargin(new LinearLayout.LayoutParams(0, -2, 1), 10, 0, 0, 0));
        panel.addView(centerSection, gap(0, 6, 0, 6));

        // Time / Seek
        d.timeView = text("00:00 / 00:00", 10, Color.GRAY, false);
        panel.addView(d.timeView);
        d.progressSeek = new SeekBar(this);
        d.progressSeek.setProgressTintList(ColorStateList.valueOf(d.themeColor));
        d.progressSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (u && d.player != null) { try { d.player.seekTo(p); } catch (Exception ignored) {} }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        panel.addView(d.progressSeek);

        // Main Transport (LOAD, CUE, PLAY)
        LinearLayout transport = new LinearLayout(this);
        Button loadBtn = button("LOAD", BG_DARK, Color.WHITE);
        loadBtn.setOnClickListener(v -> pickAudio(d.requestCode));

        d.cueBtn = button("CUE", BG_DARK, d.themeColor);
        d.cueBtn.setOnClickListener(v -> d.handleCue());

        d.playBtn = button("▶", BG_DARK, Color.WHITE);
        d.playBtn.setOnClickListener(v -> d.togglePlay());

        transport.addView(loadBtn, new LinearLayout.LayoutParams(0, -2, 1));
        transport.addView(d.cueBtn, layoutMargin(new LinearLayout.LayoutParams(0, -2, 1), 6, 0, 6, 0));
        transport.addView(d.playBtn, new LinearLayout.LayoutParams(0, -2, 1.3f));
        panel.addView(transport, gap(0, 4, 0, 8));

        // 4 Serato Performance Hot Cues
        LinearLayout cueRow = new LinearLayout(this);
        for (int i = 0; i < 4; i++) {
            final int index = i;
            d.cuePads[i] = button("HOT " + (i + 1), BG_DARK, HOT_CUE_COLORS[i]);
            d.cuePads[i].setOnClickListener(v -> d.triggerHotCue(index));
            cueRow.addView(d.cuePads[i], layoutMargin(new LinearLayout.LayoutParams(0, -2, 1), 2, 0, 2, 0));
        }
        panel.addView(cueRow);

        // Serato Stems Controls (Vocals, Melody, Bass, Drums)
        TextView stemLabel = text("STEMS ISOLATION", 8, Color.GRAY, true);
        panel.addView(stemLabel, gap(0, 8, 0, 4));

        LinearLayout stemsRow = new LinearLayout(this);
        String[] stemNames = {"VOCAL", "MELODY", "BASS", "DRUMS"};
        for (int i = 0; i < 4; i++) {
            final int sIdx = i;
            d.stemButtons[i] = button(stemNames[i], d.themeColor, BG_MAIN);
            d.stemButtons[i].setOnClickListener(v -> {
                boolean active = (d.stemButtons[sIdx].getTextColors().getDefaultColor() == BG_MAIN);
                d.stemButtons[sIdx].setBackground(round(active ? BG_DARK : d.themeColor, 6));
                d.stemButtons[sIdx].setTextColor(active ? Color.GRAY : BG_MAIN);
            });
            stemsRow.addView(d.stemButtons[i], layoutMargin(new LinearLayout.LayoutParams(0, -2, 1), 2, 0, 2, 0));
        }
        panel.addView(stemsRow);

        return panel;
    }

    private void pickAudio(int req) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (req == 101) deckA.load(uri);
            else if (req == 102) deckB.load(uri);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deckA.release();
        deckB.release();
        handler.removeCallbacks(loop);
    }

    // --- CUSTOM SERATO-STYLE VIEWS ---
    class TriColorWaveView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;
        private final int primary;

        TriColorWaveView(Context c, int prim) {
            super(c); primary = prim;
        }

        void setProgress(float p) {
            this.progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            paint.setColor(BG_PANEL);
            c.drawRoundRect(0, 0, w, h, 8, 8, paint);

            float mid = h / 2f;
            paint.setStrokeWidth(dp(2));

            // Serato-style multi-frequency simulated spectrum
            for (int x = 4; x < w - 4; x += dp(3)) {
                float bass = (float) (Math.abs(Math.sin(x * 0.05)) * (h * 0.38));
                float midFreq = (float) (Math.abs(Math.cos(x * 0.12)) * (h * 0.25));
                float high = (float) (Math.abs(Math.sin(x * 0.25)) * (h * 0.15));

                boolean past = (x <= progress * w);

                // Bass layer (Red/Orange)
                paint.setColor(past ? Color.rgb(255, 60, 40) : Color.rgb(90, 30, 20));
                c.drawLine(x, mid - bass, x, mid + bass, paint);

                // Mid layer (Cyan/Green)
                paint.setColor(past ? primary : Color.rgb(20, 60, 60));
                c.drawLine(x, mid - midFreq, x, mid + midFreq, paint);

                // High layer (White/Blue)
                paint.setColor(past ? Color.WHITE : Color.DKGRAY);
                c.drawLine(x, mid - high, x, mid + high, paint);
            }

            // High-contrast playhead
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(2));
            c.drawLine(progress * w, 0, progress * w, h, paint);
        }
    }

    class JogWheelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private float angle = 0f;

        JogWheelView(Context c, int col) {
            super(c); accent = col;
        }

        void rotate(float deg) {
            angle = (angle + deg) % 360f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - dp(3);

            // Vinyl Outer Platter
            paint.setColor(Color.rgb(18, 22, 30));
            c.drawCircle(cx, cy, r, paint);

            // Grooves
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(35, 42, 55));
            paint.setStrokeWidth(dp(1.5f));
            c.drawCircle(cx, cy, r * 0.75f, paint);
            c.drawCircle(cx, cy, r * 0.5f, paint);

            // Center Label & Rotating Needle
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            c.drawCircle(cx, cy, r * 0.28f, paint);

            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(2));
            float rad = (float) Math.toRadians(angle);
            c.drawLine(cx, cy, cx + (float) Math.cos(rad) * (r * 0.9f), cy + (float) Math.sin(rad) * (r * 0.9f), paint);
        }
    }

    // --- HELPERS ---
    private LinearLayout panel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(10), dp(10), dp(10), dp(10));
        l.setBackground(round(BG_PANEL, 10));
        return l;
    }

    private TextView text(String s, int sp, int col, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(col);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int bg, int textCol) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(9); b.setTextColor(textCol);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(round(bg, 6));
        return b;
    }

    private GradientDrawable round(int col, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(col); d.setCornerRadius(dp(r));
        return d;
    }

    private LinearLayout.LayoutParams gap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams layoutMargin(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private String fmt(int ms) {
        int s = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
