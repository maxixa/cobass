package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.TrackItem;
import com.maxica.cobass.plugin.PluginHostManager;

import java.util.List;

public class FxRackDialog extends Dialog {

    private final TrackItem track;
    private LinearLayout rackContainer;

    public FxRackDialog(@NonNull Context context, TrackItem track) {
        super(context);
        this.track = track;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_fx_rack);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView txtTitle = findViewById(R.id.txtFxTitle);
        txtTitle.setText("FX Rack: " + track.getName());

        rackContainer = findViewById(R.id.rackSlotsContainer);
        refreshModularRackSlots();

        // 1. Parametric EQ Sliders
        SeekBar seekEqLow = findViewById(R.id.seekEqLow);
        SeekBar seekEqMid = findViewById(R.id.seekEqMid);
        SeekBar seekEqHigh = findViewById(R.id.seekEqHigh);

        seekEqLow.setProgress((int) (((track.getEqLow() - (-18.0f)) / 36.0f) * 100.0f));
        seekEqMid.setProgress((int) (((track.getEqMid() - (-18.0f)) / 36.0f) * 100.0f));
        seekEqHigh.setProgress((int) (((track.getEqHigh() - (-18.0f)) / 36.0f) * 100.0f));

        seekEqLow.setOnSeekBarChangeListener(createFxListener(0, 1, 36.0f, -18.0f, track::setEqLow));
        seekEqMid.setOnSeekBarChangeListener(createFxListener(0, 2, 36.0f, -18.0f, track::setEqMid));
        seekEqHigh.setOnSeekBarChangeListener(createFxListener(0, 4, 36.0f, -18.0f, track::setEqHigh));

        // 2. Compressor Sliders
        SeekBar seekCompThresh = findViewById(R.id.seekCompThresh);
        SeekBar seekCompRatio = findViewById(R.id.seekCompRatio);

        seekCompThresh.setProgress((int) (((track.getCompThresh() - (-40.0f)) / 40.0f) * 100.0f));
        seekCompRatio.setProgress((int) (((track.getCompRatio() - 1.0f) / 19.0f) * 100.0f));

        seekCompThresh.setOnSeekBarChangeListener(createFxListener(1, 1, 40.0f, -40.0f, track::setCompThresh));
        seekCompRatio.setOnSeekBarChangeListener(createFxListener(1, 2, 19.0f, 1.0f, track::setCompRatio));

        // 3. Reverb & Delay Wet Mix Sliders
        SeekBar seekReverbMix = findViewById(R.id.seekReverbMix);
        SeekBar seekDelayMix = findViewById(R.id.seekDelayMix);

        seekReverbMix.setProgress((int) (track.getReverbMix() * 100.0f));
        seekDelayMix.setProgress((int) (track.getDelayMix() * 100.0f));

        seekReverbMix.setOnSeekBarChangeListener(createFxListener(3, 3, 1.0f, 0.0f, track::setReverbMix));
        seekDelayMix.setOnSeekBarChangeListener(createFxListener(2, 3, 1.0f, 0.0f, track::setDelayMix));

        Button btnClose = findViewById(R.id.btnCloseFx);
        btnClose.setOnClickListener(v -> dismiss());
    }

    private void refreshModularRackSlots() {
        if (rackContainer == null) return;
        rackContainer.removeAllViews();

        for (int i = 0; i < 8; i++) {
            final int slotIndex = i;
            String pluginId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetTrackFxPluginId(track.getId(), slotIndex) : "";

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.parseColor("#14161E"));
            row.setPadding(10, 8, 10, 8);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 4, 0, 4);
            row.setLayoutParams(rowLp);

            TextView txtSlotNum = new TextView(getContext());
            txtSlotNum.setText("[" + (slotIndex + 1) + "]");
            txtSlotNum.setTextColor(Color.parseColor("#8E8E93"));
            txtSlotNum.setTextSize(11f);
            txtSlotNum.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(txtSlotNum);

            if (pluginId.isEmpty()) {
                // Empty Slot
                Button btnAdd = new Button(getContext());
                btnAdd.setText("+ Add Insert Plugin");
                btnAdd.setTextSize(10f);
                btnAdd.setTextColor(Color.parseColor("#8E8E93"));
                btnAdd.setBackgroundColor(Color.parseColor("#20232E"));
                LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80);
                addLp.setMargins(8, 0, 0, 0);
                btnAdd.setLayoutParams(addLp);
                btnAdd.setOnClickListener(v -> showPluginBrowserDialog(slotIndex));
                row.addView(btnAdd);
            } else {
                // Loaded Plugin Slot
                PluginDescriptorItem desc = PluginHostManager.getInstance().findPluginById(pluginId);
                String displayName = desc != null ? desc.getName() : pluginId;

                TextView txtName = new TextView(getContext());
                txtName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                txtName.setText(displayName);
                txtName.setTextColor(Color.WHITE);
                txtName.setTextSize(12f);
                txtName.setTypeface(null, android.graphics.Typeface.BOLD);
                txtName.setPadding(10, 0, 4, 0);
                row.addView(txtName);

                // Bypass Toggle
                boolean isBypassed = AudioEngineNative.isLoaded() && AudioEngineNative.nativeIsTrackFxBypassed(track.getId(), slotIndex);
                Button btnBypass = new Button(getContext());
                btnBypass.setText(isBypassed ? "BYPASS" : "ACTIVE");
                btnBypass.setTextSize(9f);
                btnBypass.setTextColor(isBypassed ? Color.parseColor("#FF453A") : Color.parseColor("#30D158"));
                btnBypass.setBackgroundColor(isBypassed ? Color.parseColor("#4D1E24") : Color.parseColor("#163824"));
                btnBypass.setLayoutParams(new LinearLayout.LayoutParams(130, 75));
                btnBypass.setOnClickListener(v -> {
                    boolean nextState = !isBypassed;
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeSetTrackFxBypass(track.getId(), slotIndex, nextState);
                    }
                    refreshModularRackSlots();
                });
                row.addView(btnBypass);

                // Edit Plugin UI Button
                Button btnEdit = new Button(getContext());
                btnEdit.setText("⚙ EDIT");
                btnEdit.setTextSize(9f);
                btnEdit.setTextColor(Color.WHITE);
                btnEdit.setBackgroundColor(Color.parseColor("#0A84FF"));
                LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(120, 75);
                editLp.setMargins(4, 0, 0, 0);
                btnEdit.setLayoutParams(editLp);
                btnEdit.setOnClickListener(v -> {
                    if (desc != null) {
                        PluginUiDialog dialog = new PluginUiDialog(getContext(), track.getId(), slotIndex, desc, this::refreshModularRackSlots);
                        dialog.show();
                    }
                });
                row.addView(btnEdit);

                // Move Up Button
                Button btnUp = new Button(getContext());
                btnUp.setText("▲");
                btnUp.setTextSize(9f);
                btnUp.setTextColor(Color.WHITE);
                btnUp.setBackgroundColor(Color.parseColor("#2C2F3C"));
                LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(65, 75);
                upLp.setMargins(4, 0, 0, 0);
                btnUp.setLayoutParams(upLp);
                btnUp.setOnClickListener(v -> {
                    if (slotIndex > 0 && AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeMoveTrackFxSlot(track.getId(), slotIndex, slotIndex - 1);
                        refreshModularRackSlots();
                    }
                });
                row.addView(btnUp);

                // Move Down Button
                Button btnDown = new Button(getContext());
                btnDown.setText("▼");
                btnDown.setTextSize(9f);
                btnDown.setTextColor(Color.WHITE);
                btnDown.setBackgroundColor(Color.parseColor("#2C2F3C"));
                LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(65, 75);
                downLp.setMargins(2, 0, 0, 0);
                btnDown.setLayoutParams(downLp);
                btnDown.setOnClickListener(v -> {
                    if (slotIndex < 7 && AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeMoveTrackFxSlot(track.getId(), slotIndex, slotIndex + 1);
                        refreshModularRackSlots();
                    }
                });
                row.addView(btnDown);

                // Remove Button
                Button btnDel = new Button(getContext());
                btnDel.setText("✕");
                btnDel.setTextSize(10f);
                btnDel.setTextColor(Color.WHITE);
                btnDel.setBackgroundColor(Color.parseColor("#FF453A"));
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(65, 75);
                delLp.setMargins(4, 0, 0, 0);
                btnDel.setLayoutParams(delLp);
                btnDel.setOnClickListener(v -> {
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeRemoveTrackFxPlugin(track.getId(), slotIndex);
                    }
                    refreshModularRackSlots();
                });
                row.addView(btnDel);
            }

            rackContainer.addView(row);
        }
    }

    private void showPluginBrowserDialog(int slotIndex) {
        Dialog browserDialog = new Dialog(getContext());
        browserDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("Insert FX Plugin Browser (Slot " + (slotIndex + 1) + ")");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        List<PluginDescriptorItem> effects = PluginHostManager.getInstance().getEffectPlugins();
        if (effects.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No modular FX plugins discovered in app library.");
            empty.setTextColor(Color.parseColor("#8E8E93"));
            empty.setPadding(0, 16, 0, 16);
            layout.addView(empty);
        } else {
            for (PluginDescriptorItem fx : effects) {
                Button btn = new Button(getContext());
                btn.setText(fx.getName() + " (" + fx.getVendor() + ")");
                btn.setTextSize(11f);
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(Color.parseColor("#242734"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 4, 0, 4);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> {
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeAddTrackFxPlugin(track.getId(), slotIndex, fx.getPluginId());
                    }
                    browserDialog.dismiss();
                    refreshModularRackSlots();
                    Toast.makeText(getContext(), "Loaded " + fx.getName() + " into Slot " + (slotIndex + 1), Toast.LENGTH_SHORT).show();
                });
                layout.addView(btn);
            }
        }

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> browserDialog.dismiss());
        layout.addView(btnCancel);

        browserDialog.setContentView(scroll);
        if (browserDialog.getWindow() != null) {
            browserDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            browserDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        browserDialog.show();
    }

    private interface ParamPersistCallback {
        void onParamSaved(float value);
    }

    private SeekBar.OnSeekBarChangeListener createFxListener(int fxSlot, int paramId, float range, float offset, ParamPersistCallback persistCb) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float value = offset + ((float) progress / seekBar.getMax()) * range;
                    persistCb.onParamSaved(value);
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeSetTrackFxParam(track.getId(), fxSlot, paramId, value);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }
}
