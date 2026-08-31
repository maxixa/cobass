package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.TrackItem;

import java.util.ArrayList;
import java.util.List;

public class MixerConsoleDialog extends Dialog {

    private static class StripHolder {
        TrackItem track;
        TextView txtTrackName;
        SeekBar fader;
        Button btnMute;
        Button btnSolo;
        Button btnPhase;
        ProgressBar meterL;
        ProgressBar meterR;
    }

    private final List<TrackItem> tracks;
    private final Runnable onDismissCallback;
    private final List<StripHolder> holders = new ArrayList<>();
    private final Handler vuMeterHandler = new Handler(Looper.getMainLooper());
    private boolean isRunning = true;
    private boolean isMasterLimiterOn = true;

    public MixerConsoleDialog(@NonNull Context context, List<TrackItem> tracks, Runnable onDismissCallback) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.tracks = tracks;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_mixer_console);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#121316")));
        }

        LinearLayout stripContainer = findViewById(R.id.stripContainer);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        holders.clear();

        for (TrackItem track : tracks) {
            View strip = inflater.inflate(R.layout.item_mixer_strip, stripContainer, false);

            TextView txtTrackName = strip.findViewById(R.id.txtStripName);
            txtTrackName.setText(track.getName());

            SeekBar fader = strip.findViewById(R.id.faderVolume);
            SeekBar pan = strip.findViewById(R.id.faderPan);
            Button btnMute = strip.findViewById(R.id.btnStripMute);
            Button btnSolo = strip.findViewById(R.id.btnStripSolo);
            Button btnPhase = strip.findViewById(R.id.btnStripPhase);
            Button btnFx = strip.findViewById(R.id.btnStripFx);
            ProgressBar meterL = strip.findViewById(R.id.meterL);
            ProgressBar meterR = strip.findViewById(R.id.meterR);

            StripHolder holder = new StripHolder();
            holder.track = track;
            holder.txtTrackName = txtTrackName;
            holder.fader = fader;
            holder.btnMute = btnMute;
            holder.btnSolo = btnSolo;
            holder.btnPhase = btnPhase;
            holder.meterL = meterL;
            holder.meterR = meterR;
            holders.add(holder);

            fader.setProgress((int) (track.getVolume() * 100));
            pan.setProgress((int) ((track.getPan() + 1.0f) * 50));

            btnMute.setOnClickListener(v -> {
                track.setMuted(!track.isMuted());
                AudioEngineNative.nativeSetTrackMute(track.getId(), track.isMuted());
                updateAllStripsMuteSoloUI();
            });

            btnSolo.setOnClickListener(v -> {
                track.setSolo(!track.isSolo());
                AudioEngineNative.nativeSetTrackSolo(track.getId(), track.isSolo());
                updateAllStripsMuteSoloUI();
            });

            btnPhase.setOnClickListener(v -> {
                boolean inverted = !track.isPhaseInverted();
                track.setPhaseInverted(inverted);
                AudioEngineNative.nativeSetTrackPhaseInvert(track.getId(), inverted);
                btnPhase.setBackgroundColor(inverted ? Color.parseColor("#0A84FF") : Color.parseColor("#3A3A3C"));
            });

            fader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float vol = progress / 100.0f;
                        track.setVolume(vol);
                        AudioEngineNative.nativeSetTrackVolume(track.getId(), vol);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            pan.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float p = (progress / 50.0f) - 1.0f;
                        track.setPan(p);
                        AudioEngineNative.nativeSetTrackPan(track.getId(), p);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            btnFx.setOnClickListener(v -> {
                FxRackDialog fxDialog = new FxRackDialog(getContext(), track);
                fxDialog.show();
            });

            stripContainer.addView(strip);
        }

        // Master Controls
        SeekBar masterFader = findViewById(R.id.masterFader);
        Button btnMasterLimiter = findViewById(R.id.btnMasterLimiter);

        btnMasterLimiter.setOnClickListener(v -> {
            isMasterLimiterOn = !isMasterLimiterOn;
            AudioEngineNative.nativeSetMasterLimiter(isMasterLimiterOn);
            btnMasterLimiter.setText(isMasterLimiterOn ? "LIMITER ON" : "LIMITER OFF");
            btnMasterLimiter.setBackgroundColor(isMasterLimiterOn ? Color.parseColor("#30D158") : Color.parseColor("#3A3A3C"));
        });

        masterFader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    AudioEngineNative.nativeSetMasterVolume(progress / 50.0f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Button btnClose = findViewById(R.id.btnCloseMixer);
        btnClose.setOnClickListener(v -> dismiss());

        updateAllStripsMuteSoloUI();
        startVuMeterLoop();
    }

    private void updateAllStripsMuteSoloUI() {
        boolean anySolo = false;
        for (StripHolder h : holders) {
            if (h.track.isSolo()) {
                anySolo = true;
                break;
            }
        }

        for (StripHolder h : holders) {
            boolean isSolo = h.track.isSolo();
            boolean isMuted = h.track.isMuted();
            boolean silencedBySolo = anySolo && !isSolo;

            // Solo Button Visual State
            h.btnSolo.setBackgroundColor(isSolo ? Color.parseColor("#FFD60A") : Color.parseColor("#3A3A3C"));
            h.btnSolo.setTextColor(isSolo ? Color.BLACK : Color.WHITE);

            // Mute Button & Dimming State
            if (isMuted) {
                h.btnMute.setBackgroundColor(Color.parseColor("#FF453A"));
                h.btnMute.setAlpha(1.0f);
            } else if (silencedBySolo) {
                h.btnMute.setBackgroundColor(Color.parseColor("#262936"));
                h.btnMute.setAlpha(0.45f);
            } else {
                h.btnMute.setBackgroundColor(Color.parseColor("#3A3A3C"));
                h.btnMute.setAlpha(1.0f);
            }

            h.btnPhase.setBackgroundColor(h.track.isPhaseInverted() ? Color.parseColor("#0A84FF") : Color.parseColor("#3A3A3C"));
            h.fader.setAlpha((isMuted || silencedBySolo) ? 0.45f : 1.0f);
            h.txtTrackName.setTextColor((isMuted || silencedBySolo) ? Color.parseColor("#8E8E93") : Color.parseColor("#F2F2F7"));
        }
    }

    private void startVuMeterLoop() {
        vuMeterHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isRunning && AudioEngineNative.isLoaded()) {
                    for (StripHolder h : holders) {
                        float pL = AudioEngineNative.nativeGetTrackPeakL(h.track.getId());
                        float pR = AudioEngineNative.nativeGetTrackPeakR(h.track.getId());
                        h.meterL.setProgress((int) (Math.min(1.0f, pL) * 100));
                        h.meterR.setProgress((int) (Math.min(1.0f, pR) * 100));
                    }

                    ProgressBar masterMeterL = findViewById(R.id.masterMeterL);
                    ProgressBar masterMeterR = findViewById(R.id.masterMeterR);
                    if (masterMeterL != null && masterMeterR != null) {
                        float pL = AudioEngineNative.nativeGetMasterPeakL();
                        float pR = AudioEngineNative.nativeGetMasterPeakR();
                        masterMeterL.setProgress((int) (Math.min(1.0f, pL) * 100));
                        masterMeterR.setProgress((int) (Math.min(1.0f, pR) * 100));
                    }
                }
                if (isRunning) vuMeterHandler.postDelayed(this, 30);
            }
        });
    }

    @Override
    public void dismiss() {
        super.dismiss();
        isRunning = false;
        vuMeterHandler.removeCallbacksAndMessages(null);
        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }
}
