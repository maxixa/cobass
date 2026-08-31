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
import android.widget.LinearLayout;
import android.widget.TextView;
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

        buildParameterMatrix(paramContainer);
    }

    private void updateBypassButtonState(Button btnBypass) {
        if (btnBypass == null) return;
        btnBypass.setText(isBypassed ? "BYPASS: ON" : "⚡ ACTIVE");
        btnBypass.setTextColor(isBypassed ? Color.parseColor("#FF453A") : Color.parseColor("#30D158"));
        btnBypass.setBackgroundColor(isBypassed ? Color.parseColor("#4D1E24") : Color.parseColor("#163824"));
    }

    private void buildParameterMatrix(LinearLayout container) {
        container.removeAllViews();
        activeKnobs.clear();

        List<PluginParamItem> params = descriptor.getParameters();
        if (params.isEmpty()) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("This plugin does not expose adjustable parameters.");
            emptyText.setTextColor(Color.parseColor("#8E8E93"));
            emptyText.setPadding(20, 40, 20, 40);
            emptyText.setGravity(Gravity.CENTER);
            container.addView(emptyText);
            return;
        }

        final int columnsPerRow = 4;
        LinearLayout currentRow = null;

        for (int i = 0; i < params.size(); i++) {
            if (i % columnsPerRow == 0) {
                currentRow = new LinearLayout(getContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setGravity(Gravity.CENTER_VERTICAL);
                currentRow.setPadding(0, 8, 0, 8);
                container.addView(currentRow);
            }

            PluginParamItem p = params.get(i);
            View paramView = createParamControlView(p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(6, 0, 6, 0);
            paramView.setLayoutParams(lp);
            if (currentRow != null) currentRow.addView(paramView);
        }
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
                }
            });
        }
    }

    private void syncVisualizerFromParam(String name, float value) {
        if (visualizerView == null) return;
        String lower = name.toLowerCase();
        if (lower.contains("cutoff")) {
            visualizerView.setFilterParams(value, 1.5f);
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
