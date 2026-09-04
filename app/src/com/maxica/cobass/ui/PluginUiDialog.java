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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.PluginParamItem;

import java.util.ArrayList;
import java.util.List;

public class PluginUiDialog extends Dialog implements PluginPresetDialog.OnPresetActionListener {

    private final int trackId;
    private final int slotIndex;
    private final PluginDescriptorItem descriptor;
    private final Runnable onDismissCallback;

    private boolean isBypassed = false;
    private String stateA = "{}";
    private String stateB = "{}";
    private boolean isStateActiveA = true;

    private SynthVisualizerView visualizerView;
    private final List<RotaryKnobView> activeKnobs = new ArrayList<>();
    private int activeCategoryFilter = 0; // 0 = ALL

    public PluginUiDialog(@NonNull Context context, int trackId, int slotIndex,
                          PluginDescriptorItem descriptor, Runnable onDismissCallback) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.trackId = trackId;
        this.slotIndex = slotIndex;
        this.descriptor = descriptor;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_plugin_host);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#121316")));
        }

        TextView txtTitle = findViewById(R.id.txtPluginTitle);
        TextView txtVendor = findViewById(R.id.txtPluginVendor);
        Button btnVariation = findViewById(R.id.btnPluginVariation);
        Button btnBypass = findViewById(R.id.btnPluginBypass);
        Button btnAb = findViewById(R.id.btnAbCompare);
        Button btnPresets = findViewById(R.id.btnPluginPresets);
        Button btnSavePatch = findViewById(R.id.btnSavePatch);
        Button btnClose = findViewById(R.id.btnClosePluginDialog);
        FrameLayout visualizerContainer = findViewById(R.id.pluginVisualizerContainer);
        LinearLayout paramContainer = findViewById(R.id.paramMatrixContainer);

        txtTitle.setText(descriptor.getName());
        txtVendor.setText("v" + descriptor.getVersion() + " • " + descriptor.getVendor());

        if (visualizerContainer != null) {
            visualizerContainer.removeAllViews();
            visualizerView = new SynthVisualizerView(getContext());
            if (descriptor.getPluginId().contains("drums")) {
                visualizerView.setDisplayMode(SynthVisualizerView.DisplayMode.DRUM_MATRIX_HUD);
            }
            visualizerContainer.addView(visualizerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (AudioEngineNative.isLoaded()) {
            stateA = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
            stateB = stateA;
            if (slotIndex >= 0) {
                isBypassed = AudioEngineNative.nativeIsTrackFxBypassed(trackId, slotIndex);
            }
        }
        updateBypassButtonState(btnBypass);

        if (btnVariation != null) {
            btnVariation.setOnClickListener(v -> {
                new VariationStudioDialog(
                    getContext(),
                    trackId,
                    slotIndex,
                    descriptor,
                    this::refreshAllKnobValues,
                    this
                ).show();
            });
        }

        btnBypass.setOnClickListener(v -> {
            isBypassed = !isBypassed;
            if (AudioEngineNative.isLoaded() && slotIndex >= 0) {
                AudioEngineNative.nativeSetTrackFxBypass(trackId, slotIndex, isBypassed);
            }
            updateBypassButtonState(btnBypass);
        });

        btnAb.setOnClickListener(v -> {
            if (!AudioEngineNative.isLoaded()) return;
            if (isStateActiveA) {
                stateA = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
                AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, stateB);
                isStateActiveA = false;
                btnAb.setText("STATE B");
                btnAb.setTextColor(Color.parseColor("#FFD60A"));
            } else {
                stateB = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
                AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, stateA);
                isStateActiveA = true;
                btnAb.setText("STATE A");
                btnAb.setTextColor(Color.parseColor("#0A84FF"));
            }
            refreshAllKnobValues();
        });

        btnPresets.setOnClickListener(v -> new PluginPresetDialog(getContext(), descriptor, this).show());
        btnSavePatch.setOnClickListener(v -> PluginPresetDialog.showSaveDialog(getContext(), descriptor, this));
        btnClose.setOnClickListener(v -> dismiss());

        buildCategorizedParameterMatrix(paramContainer);
    }

    private void updateBypassButtonState(Button btnBypass) {
        if (btnBypass == null) return;
        btnBypass.setText(isBypassed ? "BYPASS: ON" : "⚡ ACTIVE");
        btnBypass.setTextColor(isBypassed ? Color.parseColor("#FF453A") : Color.parseColor("#30D158"));
        btnBypass.setBackgroundColor(isBypassed ? Color.parseColor("#4D1E24") : Color.parseColor("#163824"));
    }

    private void buildCategorizedParameterMatrix(LinearLayout container) {
        container.removeAllViews();
        activeKnobs.clear();

        List<PluginParamItem> allParams = descriptor.getParameters();
        if (allParams.isEmpty()) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("This plugin does not expose adjustable parameters.");
            emptyText.setTextColor(Color.parseColor("#8E8E93"));
            emptyText.setPadding(20, 40, 20, 40);
            emptyText.setGravity(Gravity.CENTER);
            container.addView(emptyText);
            return;
        }

        // 1. Build Categorized Tabs & Audition Ribbon
        if (descriptor.getPluginId().contains("drums") || allParams.size() > 12) {
            HorizontalScrollView tabScroll = new HorizontalScrollView(getContext());
            tabScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout tabRow = new LinearLayout(getContext());
            tabRow.setOrientation(LinearLayout.HORIZONTAL);
            tabRow.setPadding(0, 4, 0, 10);

            String[] categories;
            if (descriptor.getPluginId().contains("drums")) {
                categories = new String[]{"ALL PARAMS", "KICK & SNARE", "HATS & CLAP", "TOMS & PERC", "MASTER"};
            } else {
                categories = new String[]{"ALL PARAMS", "OSCILLATORS & FM", "FILTER & KEYTRACK", "ENVELOPES & PUNCH", "DANCE FX SUITE", "MASTER & GLIDE"};
            }

            for (int i = 0; i < categories.length; i++) {
                final int catIdx = i;
                Button btnTab = new Button(getContext());
                btnTab.setText(categories[i]);
                btnTab.setTextSize(10f);
                btnTab.setTypeface(null, android.graphics.Typeface.BOLD);
                boolean isSel = (activeCategoryFilter == catIdx);
                btnTab.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#20232E"));
                btnTab.setTextColor(isSel ? Color.WHITE : Color.parseColor("#8E8E93"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72);
                lp.setMargins(0, 0, 8, 0);
                btnTab.setLayoutParams(lp);
                btnTab.setOnClickListener(v -> {
                    activeCategoryFilter = catIdx;
                    buildCategorizedParameterMatrix(container);
                });
                tabRow.addView(btnTab);
            }
            tabScroll.addView(tabRow);
            container.addView(tabScroll);

            // 2. Direct Audition Pad Ribbon
            LinearLayout audRow = new LinearLayout(getContext());
            audRow.setOrientation(LinearLayout.HORIZONTAL);
            audRow.setPadding(0, 0, 0, 10);

            if (descriptor.getPluginId().contains("drums")) {
                addAuditionPad(audRow, "▶ Kick", 36, Color.parseColor("#0A84FF"));
                addAuditionPad(audRow, "▶ Snare", 38, Color.parseColor("#FF9F0A"));
                addAuditionPad(audRow, "▶ Clap", 39, Color.parseColor("#30D158"));
                addAuditionPad(audRow, "▶ Cl.Hat", 42, Color.parseColor("#BF5AF2"));
                addAuditionPad(audRow, "▶ Op.Hat", 46, Color.parseColor("#FF453A"));
                addAuditionPad(audRow, "▶ Cowbell", 56, Color.parseColor("#FFD60A"));
            } else {
                addAuditionPad(audRow, "▶ C2 Sub", 36, Color.parseColor("#0A84FF"));
                addAuditionPad(audRow, "▶ C3 Bass", 48, Color.parseColor("#30D158"));
                addAuditionPad(audRow, "▶ C4 Pluck", 60, Color.parseColor("#FF9F0A"));
                addAuditionPad(audRow, "▶ C5 Lead", 72, Color.parseColor("#BF5AF2"));
            }
            container.addView(audRow);
        }

        // Filter parameters by category
        List<PluginParamItem> filteredParams = new ArrayList<>();
        for (PluginParamItem p : allParams) {
            int id = p.getId();
            if (descriptor.getPluginId().contains("drums")) {
                if (activeCategoryFilter == 0) filteredParams.add(p);
                else if (activeCategoryFilter == 1 && id >= 4 && id <= 13) filteredParams.add(p);
                else if (activeCategoryFilter == 2 && id >= 14 && id <= 22) filteredParams.add(p);
                else if (activeCategoryFilter == 3 && id >= 23 && id <= 31) filteredParams.add(p);
                else if (activeCategoryFilter == 4 && id >= 0 && id <= 3) filteredParams.add(p);
            } else {
                if (activeCategoryFilter == 0) filteredParams.add(p);
                else if (activeCategoryFilter == 1 && id >= 0 && id <= 21) filteredParams.add(p);
                else if (activeCategoryFilter == 2 && id >= 22 && id <= 28) filteredParams.add(p);
                else if (activeCategoryFilter == 3 && id >= 29 && id <= 42) filteredParams.add(p);
                else if (activeCategoryFilter == 4 && id >= 43 && id <= 51) filteredParams.add(p);
                else if (activeCategoryFilter == 5 && id >= 52) filteredParams.add(p);
            }
        }

        final int columnsPerRow = 4;
        LinearLayout currentRow = null;

        for (int i = 0; i < filteredParams.size(); i++) {
            if (i % columnsPerRow == 0) {
                currentRow = new LinearLayout(getContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setGravity(Gravity.CENTER_VERTICAL);
                currentRow.setPadding(0, 8, 0, 8);
                container.addView(currentRow);
            }

            PluginParamItem p = filteredParams.get(i);
            View paramView = createParamControlView(p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(6, 0, 6, 0);
            paramView.setLayoutParams(lp);
            if (currentRow != null) currentRow.addView(paramView);
        }
    }

    private void addAuditionPad(LinearLayout parent, String label, int midiNote, int color) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setTextSize(9f);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 68, 1.0f);
        lp.setMargins(0, 0, 4, 0);
        btn.setLayoutParams(lp);
        CobassInteraction.attachAuditionTouch(btn, trackId, midiNote, 1.0f);
        parent.addView(btn);
    }

    private View createParamControlView(PluginParamItem param) {
        float initialVal = param.getDefaultValue();
        if (AudioEngineNative.isLoaded()) {
            initialVal = AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, param.getId());
        }

        if (param.getType() == PluginParamItem.Type.FLOAT) {
            RotaryKnobView knob = PluginControlFactory.createRotaryKnob(getContext(), param, initialVal, (p, val) -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, p.getId(), val);
                    syncVisualizerFromParam(p.getName(), val);
                }
            });
            activeKnobs.add(knob);
            syncVisualizerFromParam(param.getName(), initialVal);
            return knob;
        } else if (param.getType() == PluginParamItem.Type.BOOL) {
            return PluginControlFactory.createBooleanToggle(getContext(), param, initialVal > 0.5f, (p, val) -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, p.getId(), val);
                }
            });
        } else {
            return PluginControlFactory.createChoiceStepper(getContext(), param, (int) initialVal, (p, val) -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, p.getId(), val);
                    if (p.getName().contains("Filter Mode") && visualizerView != null) {
                        visualizerView.setFilterMode((int) val);
                    }
                }
            });
        }
    }

    private void syncVisualizerFromParam(String name, float value) {
        if (visualizerView == null) return;
        String lower = name.toLowerCase();
        if (lower.contains("cutoff")) {
            visualizerView.setFilterParams(value, 1.8f);
        } else if (lower.contains("resonance")) {
            visualizerView.setFilterParams(3500.0f, value);
        } else if (lower.contains("attack")) {
            visualizerView.setEnvelopeParams(value, 120.0f, 0.70f, 250.0f);
        }
    }

    private void refreshAllKnobValues() {
        if (!AudioEngineNative.isLoaded()) return;
        for (RotaryKnobView knob : activeKnobs) {
            float val = AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, knob.getParamItem().getId());
            knob.setValue(val, false);
        }
    }

    @Override
    public String onGetPluginStateJson() {
        return AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex) : "{}";
    }

    @Override
    public void onSetPluginStateJson(String jsonState) {
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, jsonState);
            refreshAllKnobValues();
        }
    }

    @Override
    public void dismiss() {
        if (visualizerView != null) {
            visualizerView.stopAnimation();
        }
        super.dismiss();
        if (onDismissCallback != null) onDismissCallback.run();
    }
}
