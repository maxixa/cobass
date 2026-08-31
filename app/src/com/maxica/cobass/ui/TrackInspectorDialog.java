package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.TrackItem;
import com.maxica.cobass.plugin.PluginHostManager;

public class TrackInspectorDialog extends Dialog {

    public interface OnTrackInspectorActionListener {
        void onTrackUpdated(TrackItem track);
        void onMoveTrackUp(TrackItem track);
        void onMoveTrackDown(TrackItem track);
        void onDuplicateTrack(TrackItem track);
        void onDeleteTrack(TrackItem track);
        void onOpenFxRack(TrackItem track);
    }

    private final TrackItem track;
    private final OnTrackInspectorActionListener listener;

    public TrackInspectorDialog(@NonNull Context context, TrackItem track, OnTrackInspectorActionListener listener) {
        super(context);
        this.track = track;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_track_inspector);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView txtTitle = findViewById(R.id.txtInspectorTitle);
        EditText editName = findViewById(R.id.editTrackName);
        SeekBar seekVolume = findViewById(R.id.seekTrackVolume);
        SeekBar seekPan = findViewById(R.id.seekTrackPan);
        TextView txtVol = findViewById(R.id.txtVolumeReadout);
        TextView txtPan = findViewById(R.id.txtPanReadout);
        Button btnPhase = findViewById(R.id.btnTogglePhase);
        Button btnFx = findViewById(R.id.btnOpenFxRack);
        Button btnUp = findViewById(R.id.btnMoveTrackUp);
        Button btnDown = findViewById(R.id.btnMoveTrackDown);
        Button btnDupl = findViewById(R.id.btnDuplicateTrack);
        Button btnDel = findViewById(R.id.btnDeleteTrack);
        Button btnClose = findViewById(R.id.btnCloseInspector);

        View layoutInstrument = findViewById(R.id.layoutInstrumentEngine);
        TextView txtCurrentInstrument = findViewById(R.id.txtCurrentInstrument);
        Button btnChangeSynth = findViewById(R.id.btnChangeSynth);
        Button btnEditSynth = findViewById(R.id.btnEditSynth);

        if (track.getType() == TrackItem.Type.SYNTH) {
            layoutInstrument.setVisibility(View.VISIBLE);
            String synthId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetTrackSynthPluginId(track.getId()) : "";
            PluginDescriptorItem currentSynthDesc = PluginHostManager.getInstance().findPluginById(synthId);
            txtCurrentInstrument.setText(currentSynthDesc != null ? currentSynthDesc.getName() : "Cobass PolySynth (Default Engine)");

            btnChangeSynth.setOnClickListener(v -> {
                new InstrumentBrowserDialog(getContext(), new InstrumentBrowserDialog.OnInstrumentSelectedListener() {
                    @Override
                    public void onDefaultInstrumentSelected() {
                        if (AudioEngineNative.isLoaded()) {
                            AudioEngineNative.nativeRemoveTrackSynthPlugin(track.getId());
                        }
                        track.setInstrumentPluginId("");
                        track.setInstrumentPluginStateJson("{}");
                        txtCurrentInstrument.setText("Cobass PolySynth (Default Engine)");
                        Toast.makeText(getContext(), "Switched to Default PolySynth", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPluginInstrumentSelected(PluginDescriptorItem plugin) {
                        if (AudioEngineNative.isLoaded()) {
                            AudioEngineNative.nativeSetTrackSynthPlugin(track.getId(), plugin.getPluginId());
                        }
                        track.setInstrumentPluginId(plugin.getPluginId());
                        txtCurrentInstrument.setText(plugin.getName());
                        Toast.makeText(getContext(), "Loaded " + plugin.getName() + " onto track", Toast.LENGTH_SHORT).show();
                    }
                }).show();
            });

            btnEditSynth.setOnClickListener(v -> {
                String curId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetTrackSynthPluginId(track.getId()) : "";
                PluginDescriptorItem current = PluginHostManager.getInstance().findPluginById(curId);
                if (current != null) {
                    new PluginUiDialog(getContext(), track.getId(), -1, current, () -> {
                        if (listener != null) listener.onTrackUpdated(track);
                    }).show();
                } else {
                    Toast.makeText(getContext(), "Default PolySynth is active", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            layoutInstrument.setVisibility(View.GONE);
        }

        txtTitle.setText("Track Inspector: " + track.getName());
        editName.setText(track.getName());

        seekVolume.setProgress((int) (track.getVolume() * 100));
        txtVol.setText((int) (track.getVolume() * 100) + "%");

        seekPan.setProgress((int) ((track.getPan() + 1.0f) * 50));
        txtPan.setText(String.format("Pan: %+d", (int) (track.getPan() * 100)));

        btnPhase.setBackgroundColor(track.isPhaseInverted() ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));

        findViewById(R.id.btnColorBlue).setOnClickListener(v -> { track.setColor(Color.parseColor("#0A84FF")); if (listener != null) listener.onTrackUpdated(track); });
        findViewById(R.id.btnColorOrange).setOnClickListener(v -> { track.setColor(Color.parseColor("#FF9F0A")); if (listener != null) listener.onTrackUpdated(track); });
        findViewById(R.id.btnColorGreen).setOnClickListener(v -> { track.setColor(Color.parseColor("#30D158")); if (listener != null) listener.onTrackUpdated(track); });
        findViewById(R.id.btnColorPurple).setOnClickListener(v -> { track.setColor(Color.parseColor("#BF5AF2")); if (listener != null) listener.onTrackUpdated(track); });
        findViewById(R.id.btnColorRed).setOnClickListener(v -> { track.setColor(Color.parseColor("#FF453A")); if (listener != null) listener.onTrackUpdated(track); });
        findViewById(R.id.btnColorCyan).setOnClickListener(v -> { track.setColor(Color.parseColor("#64D2FF")); if (listener != null) listener.onTrackUpdated(track); });

        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float vol = progress / 100.0f;
                track.setVolume(vol);
                txtVol.setText(progress + "%");
                if (AudioEngineNative.isLoaded()) AudioEngineNative.nativeSetTrackVolume(track.getId(), vol);
                if (listener != null) listener.onTrackUpdated(track);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekPan.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float pan = (progress / 50.0f) - 1.0f;
                track.setPan(pan);
                txtPan.setText(String.format("Pan: %+d", (int) (pan * 100)));
                if (AudioEngineNative.isLoaded()) AudioEngineNative.nativeSetTrackPan(track.getId(), pan);
                if (listener != null) listener.onTrackUpdated(track);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnPhase.setOnClickListener(v -> {
            track.setPhaseInverted(!track.isPhaseInverted());
            btnPhase.setBackgroundColor(track.isPhaseInverted() ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            if (AudioEngineNative.isLoaded()) AudioEngineNative.nativeSetTrackPhaseInvert(track.getId(), track.isPhaseInverted());
        });

        btnFx.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onOpenFxRack(track);
        });

        btnUp.setOnClickListener(v -> {
            if (listener != null) listener.onMoveTrackUp(track);
        });

        btnDown.setOnClickListener(v -> {
            if (listener != null) listener.onMoveTrackDown(track);
        });

        btnDupl.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onDuplicateTrack(track);
        });

        btnDel.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onDeleteTrack(track);
        });

        btnClose.setOnClickListener(v -> {
            String newName = editName.getText().toString().trim();
            if (!newName.isEmpty()) track.setName(newName);
            if (listener != null) listener.onTrackUpdated(track);
            dismiss();
        });
    }
}
