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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.PluginParamItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PluginUiDialog extends Dialog {

    private final int trackId;
    private final int slotIndex; // -1 for Instrument Synth, 0..7 for Insert FX
    private final PluginDescriptorItem descriptor;
    private final Runnable onDismissCallback;

    private boolean isBypassed = false;
    private String stateA = "{}";
    private String stateB = "{}";
    private boolean isStateActiveA = true;

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
        LinearLayout paramContainer = findViewById(R.id.paramMatrixContainer);

        txtTitle.setText(descriptor.getName());
        txtVendor.setText("v" + descriptor.getVersion() + " • " + descriptor.getVendor());

        // Capture Initial State A
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

        btnPresets.setOnClickListener(v -> showPresetsDialog());
        btnSavePatch.setOnClickListener(v -> showSavePatchDialog());
        btnClose.setOnClickListener(v -> dismiss());

        // Build Responsive Parameter Matrix
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

        // Layout parameters in rows of 4
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
        if (param.getType() == PluginParamItem.Type.FLOAT) {
            RotaryKnobView knob = new RotaryKnobView(getContext());
            knob.setParamItem(param);

            if (AudioEngineNative.isLoaded()) {
                float val = AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, param.getId());
                knob.setValue(val, false);
            }

            knob.setOnKnobChangeListener((k, value, fromUser) -> {
                if (fromUser && AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, param.getId(), value);
                }
            });

            activeKnobs.add(knob);
            return knob;
        } else if (param.getType() == PluginParamItem.Type.BOOL) {
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(4, 12, 4, 12);

            TextView lbl = new TextView(getContext());
            lbl.setText(param.getName());
            lbl.setTextColor(Color.parseColor("#8E8E93"));
            lbl.setTextSize(11f);
            lbl.setTypeface(null, android.graphics.Typeface.BOLD);
            lbl.setGravity(Gravity.CENTER);
            box.addView(lbl);

            Button btnToggle = new Button(getContext());
            btnToggle.setTextSize(11f);
            btnToggle.setTypeface(null, android.graphics.Typeface.BOLD);

            boolean initialVal = param.getDefaultValue() > 0.5f;
            if (AudioEngineNative.isLoaded()) {
                initialVal = AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, param.getId()) > 0.5f;
            }
            updateBoolButton(btnToggle, initialVal);

            btnToggle.setOnClickListener(v -> {
                boolean current = btnToggle.getText().toString().equals("ON");
                boolean next = !current;
                updateBoolButton(btnToggle, next);
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, param.getId(), next ? 1.0f : 0.0f);
                }
            });

            box.addView(btnToggle);
            return box;
        } else {
            // Discrete Integer / Stepped Choices
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(4, 12, 4, 12);

            TextView lbl = new TextView(getContext());
            lbl.setText(param.getName());
            lbl.setTextColor(Color.parseColor("#8E8E93"));
            lbl.setTextSize(11f);
            lbl.setTypeface(null, android.graphics.Typeface.BOLD);
            lbl.setGravity(Gravity.CENTER);
            box.addView(lbl);

            LinearLayout rowStep = new LinearLayout(getContext());
            rowStep.setOrientation(LinearLayout.HORIZONTAL);
            rowStep.setGravity(Gravity.CENTER);

            Button btnMinus = new Button(getContext());
            btnMinus.setText("◀");
            btnMinus.setTextSize(10f);
            btnMinus.setBackgroundColor(Color.parseColor("#242734"));
            btnMinus.setTextColor(Color.WHITE);
            btnMinus.setLayoutParams(new LinearLayout.LayoutParams(60, 80));

            TextView txtVal = new TextView(getContext());
            txtVal.setTextSize(12f);
            txtVal.setTextColor(Color.WHITE);
            txtVal.setTypeface(null, android.graphics.Typeface.BOLD);
            txtVal.setGravity(Gravity.CENTER);
            txtVal.setPadding(8, 0, 8, 0);

            Button btnPlus = new Button(getContext());
            btnPlus.setText("▶");
            btnPlus.setTextSize(10f);
            btnPlus.setBackgroundColor(Color.parseColor("#242734"));
            btnPlus.setTextColor(Color.WHITE);
            btnPlus.setLayoutParams(new LinearLayout.LayoutParams(60, 80));

            int currentVal = (int) param.getDefaultValue();
            if (AudioEngineNative.isLoaded()) {
                currentVal = (int) AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, param.getId());
            }
            updateChoiceLabel(txtVal, param, currentVal);

            final int[] valHolder = {currentVal};
            btnMinus.setOnClickListener(v -> {
                if (valHolder[0] > (int) param.getMinValue()) {
                    valHolder[0]--;
                    updateChoiceLabel(txtVal, param, valHolder[0]);
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, param.getId(), (float) valHolder[0]);
                    }
                }
            });

            btnPlus.setOnClickListener(v -> {
                if (valHolder[0] < (int) param.getMaxValue()) {
                    valHolder[0]++;
                    updateChoiceLabel(txtVal, param, valHolder[0]);
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeSetPluginParameter(trackId, slotIndex, param.getId(), (float) valHolder[0]);
                    }
                }
            });

            rowStep.addView(btnMinus);
            rowStep.addView(txtVal);
            rowStep.addView(btnPlus);
            box.addView(rowStep);
            return box;
        }
    }

    private void updateBoolButton(Button btn, boolean state) {
        btn.setText(state ? "ON" : "OFF");
        btn.setTextColor(state ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
        btn.setBackgroundColor(state ? Color.parseColor("#163824") : Color.parseColor("#242734"));
    }

    private void updateChoiceLabel(TextView txt, PluginParamItem param, int val) {
        if (param.getType() == PluginParamItem.Type.CHOICE && !param.getChoices().isEmpty()) {
            int idx = Math.max(0, Math.min(param.getChoices().size() - 1, val));
            txt.setText(param.getChoices().get(idx));
        } else {
            txt.setText(String.valueOf(val));
        }
    }

    private void refreshAllKnobValues() {
        if (!AudioEngineNative.isLoaded()) return;
        for (RotaryKnobView knob : activeKnobs) {
            float val = AudioEngineNative.nativeGetPluginParameter(trackId, slotIndex, knob.getParamItem().getId());
            knob.setValue(val, false);
        }
    }

    // --- PRESET MANAGER & PATCH IO ---
    private void showPresetsDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("📁 Preset Library: " + descriptor.getName());
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        File presetDir = new File(getContext().getFilesDir(), "presets/" + descriptor.getPluginId());
        if (!presetDir.exists()) presetDir.mkdirs();

        File[] files = presetDir.listFiles((d, name) -> name.endsWith(".cobasspatch"));
        if (files == null || files.length == 0) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No saved user patches found. Click SAVE to create one.");
            emptyText.setTextColor(Color.parseColor("#8E8E93"));
            emptyText.setPadding(0, 16, 0, 16);
            layout.addView(emptyText);
        } else {
            for (File f : files) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 6, 0, 6);

                TextView txtName = new TextView(getContext());
                txtName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                txtName.setText(f.getName().replace(".cobasspatch", ""));
                txtName.setTextColor(Color.WHITE);
                txtName.setTextSize(13f);

                Button btnLoad = new Button(getContext());
                btnLoad.setText("LOAD");
                btnLoad.setTextSize(10f);
                btnLoad.setBackgroundColor(Color.parseColor("#0A84FF"));
                btnLoad.setOnClickListener(v -> {
                    loadPatchFromFile(f);
                    dialog.dismiss();
                });

                Button btnDel = new Button(getContext());
                btnDel.setText("✕");
                btnDel.setTextSize(10f);
                btnDel.setBackgroundColor(Color.parseColor("#FF453A"));
                btnDel.setOnClickListener(v -> {
                    f.delete();
                    dialog.dismiss();
                    showPresetsDialog();
                });

                row.addView(txtName);
                row.addView(btnLoad);
                row.addView(btnDel);
                layout.addView(row);
            }
        }

        Button btnDone = new Button(getContext());
        btnDone.setText("Close");
        btnDone.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnDone);

        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void showSavePatchDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);

        TextView title = new TextView(getContext());
        title.setText("💾 Save User Patch Preset");
        title.setTextColor(Color.parseColor("#30D158"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        EditText editName = new EditText(getContext());
        editName.setHint("Patch Name");
        editName.setTextColor(Color.WHITE);
        editName.setHintTextColor(Color.parseColor("#8E8E93"));
        editName.setSingleLine(true);
        layout.addView(editName);

        Button btnSave = new Button(getContext());
        btnSave.setText("Save Preset (.cobasspatch)");
        btnSave.setBackgroundColor(Color.parseColor("#30D158"));
        btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            if (name.isEmpty()) name = "User_Patch";

            File presetDir = new File(getContext().getFilesDir(), "presets/" + descriptor.getPluginId());
            if (!presetDir.exists()) presetDir.mkdirs();

            File targetFile = new File(presetDir, name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cobasspatch");
            savePatchToFile(targetFile);
            dialog.dismiss();
            Toast.makeText(getContext(), "Saved: " + targetFile.getName(), Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnSave);

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void savePatchToFile(File file) {
        if (!AudioEngineNative.isLoaded()) return;
        String json = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception ignored) {}
    }

    private void loadPatchFromFile(File file) {
        if (!AudioEngineNative.isLoaded()) return;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int read = fis.read(buf);
            if (read > 0) {
                String json = new String(buf, 0, read, StandardCharsets.UTF_8);
                AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, json);
                refreshAllKnobValues();
                Toast.makeText(getContext(), "Loaded: " + file.getName(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (onDismissCallback != null) onDismissCallback.run();
    }
}
