package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.MusicalScale;
import com.maxica.cobass.model.TransformLockMasks;
import com.maxica.cobass.model.TransformRecipeItem;
import com.maxica.cobass.sequencer.MidiTransformEngine;
import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MidiTransformStudioDialog extends Dialog {

        public interface OnLivePreviewListener {
        void onLiveNotesPreview(List<ClipItem.Note> previewNotes);
    }

public interface OnTransformCommittedListener {
        void onTransformCommitted();
    }

        private OnLivePreviewListener livePreviewListener = null;
    private boolean isGhostEnabled = true;
    private boolean isAbComparingOriginal = false;

private final List<ClipItem> targetClips = new ArrayList<>();
    private final ClipItem singleClip;
    private final MusicalScale scale;
    private final int rootKey;
    private final OnTransformCommittedListener commitListener;

    // Multi-Pass Recipe Stack Pipeline
    private final List<TransformRecipeItem> recipeStack = new ArrayList<>();
    private int activePassIndex = 0;

    private int activeCategory = 0; // 0=Rhythm, 1=Melodic, 2=Harmony, 3=Groove
    private float dryWetRatio = 1.0f;
    private final TransformLockMasks lockMasks = new TransformLockMasks();
    private List<ClipItem.Note> previewNotes = new ArrayList<>();
    private boolean isAuditioning = false;

    // View References
    private TextView txtStudioScope;
    private TextView txtStackHeader;
    private LinearLayout stackCardsContainer;
    private TextView txtSelectedOperator;
    private TextView txtIntensityLabel;
    private Button btnNewSeed;
    private LinearLayout operatorButtonsContainer;
    private TextView txtParam1Label;
    private SeekBar seekParam1;
    private TextView txtParam2Label;
    private SeekBar seekParam2;
    private TextView txtDryWetLabel;
    private SeekBar seekIntensity;

        public MidiTransformStudioDialog setLivePreviewListener(OnLivePreviewListener listener) {
        this.livePreviewListener = listener;
        recalculatePreview();
        return this;
    }

public MidiTransformStudioDialog(
        @NonNull Context context,
        ClipItem clip,
        MusicalScale scale,
        int rootKey,
        OnTransformCommittedListener commitListener
    ) {
        super(context);
        this.singleClip = clip;
        if (clip != null) this.targetClips.add(clip);
        this.scale = scale != null ? scale : MusicalScale.MAJOR;
        this.rootKey = Math.max(0, Math.min(11, rootKey));
        this.commitListener = commitListener;
        this.recipeStack.add(MidiTransformEngine.createEuclideanSliceRecipe(8, 5, 0.5f, 12345));
    }

    public MidiTransformStudioDialog(
        @NonNull Context context,
        List<ClipItem> clips,
        MusicalScale scale,
        int rootKey,
        OnTransformCommittedListener commitListener
    ) {
        super(context);
        this.singleClip = (clips != null && !clips.isEmpty()) ? clips.get(0) : null;
        if (clips != null) this.targetClips.addAll(clips);
        this.scale = scale != null ? scale : MusicalScale.MAJOR;
        this.rootKey = Math.max(0, Math.min(11, rootKey));
        this.commitListener = commitListener;
        this.recipeStack.add(MidiTransformEngine.createEuclideanSliceRecipe(8, 5, 0.5f, 12345));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_midi_transform_studio);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        txtStudioScope = findViewById(R.id.txtStudioScope);
        TextView txtStudioScale = findViewById(R.id.txtStudioScale);
        txtStackHeader = findViewById(R.id.txtStackHeader);
        stackCardsContainer = findViewById(R.id.stackCardsContainer);
        txtSelectedOperator = findViewById(R.id.txtSelectedOperator);
        txtIntensityLabel = findViewById(R.id.txtIntensityLabel);
        btnNewSeed = findViewById(R.id.btnNewSeed);
        operatorButtonsContainer = findViewById(R.id.operatorButtonsContainer);
        txtParam1Label = findViewById(R.id.txtParam1Label);
        seekParam1 = findViewById(R.id.seekParam1);
        txtParam2Label = findViewById(R.id.txtParam2Label);
        seekParam2 = findViewById(R.id.seekParam2);
        txtDryWetLabel = findViewById(R.id.txtDryWetLabel);
        seekIntensity = findViewById(R.id.seekIntensity);
        SeekBar seekDryWet = findViewById(R.id.seekDryWet);

        Button btnMacroPresets = findViewById(R.id.btnMacroPresets);
        Button btnAddPass = findViewById(R.id.btnAddPass);
        Button btnCloseStudio = findViewById(R.id.btnCloseStudio);

        Button btnCatRhythm = findViewById(R.id.btnCatRhythm);
        Button btnCatMelodic = findViewById(R.id.btnCatMelodic);
        Button btnCatHarmony = findViewById(R.id.btnCatHarmony);
        Button btnCatGroove = findViewById(R.id.btnCatGroove);

        Button btnLockDownbeats = findViewById(R.id.btnLockDownbeats);
        Button btnLockPitches = findViewById(R.id.btnLockPitches);
        Button btnLockRhythm = findViewById(R.id.btnLockRhythm);
        Button btnLockVelocities = findViewById(R.id.btnLockVelocities);
        Button btnLockBass = findViewById(R.id.btnLockBass);

        Button btnAuditionPreview = findViewById(R.id.btnAuditionPreview);
        Button btnCommitTransform = findViewById(R.id.btnCommitTransform);

        // Scope and Scale readouts
        String[] rootNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        txtStudioScale.setText("🎹 " + rootNames[rootKey] + " " + scale.getLabel());

        if (targetClips.size() > 1) {
            txtStudioScope.setText("Scope: " + targetClips.size() + " Arranger Clips");
        } else if (singleClip != null) {
            int selCount = singleClip.getSelectedNotes().size();
            txtStudioScope.setText(selCount > 0 ? ("Scope: " + selCount + " Selected Notes") : ("Scope: All Notes (" + singleClip.getNotes().size() + ")"));
        }

        // Macro Presets Dialog Launcher
        if (btnMacroPresets != null) {
            btnMacroPresets.setOnClickListener(v -> showMacroPresetsDialog());
        }

        // Add Pass Action
        if (btnAddPass != null) {
            btnAddPass.setOnClickListener(v -> {
                if (recipeStack.size() < 6) {
                    int nextSeed = new Random().nextInt(90000) + 10000;
                    recipeStack.add(MidiTransformEngine.createMarkovDriftRecipe(0.40f, nextSeed));
                    activePassIndex = recipeStack.size() - 1;
                    refreshStackCardsUI();
                    syncActivePassToUI();
                    recalculatePreview();
                } else {
                    Toast.makeText(getContext(), "Max 6 stacked passes allowed", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Category Tab Listeners
        btnCatRhythm.setOnClickListener(v -> switchCategory(0));
        btnCatMelodic.setOnClickListener(v -> switchCategory(1));
        btnCatHarmony.setOnClickListener(v -> switchCategory(2));
        btnCatGroove.setOnClickListener(v -> switchCategory(3));

        // Seed Generator
        btnNewSeed.setOnClickListener(v -> {
            TransformRecipeItem pass = getActivePass();
            if (pass != null) {
                pass.seed = new Random().nextInt(90000) + 10000;
                btnNewSeed.setText("🎲 SEED: #" + pass.seed);
                refreshStackCardsUI();
                recalculatePreview();
            }
        });

        // Intensity Slider for active pass
        seekIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                TransformRecipeItem pass = getActivePass();
                if (pass != null) {
                    pass.intensity = Math.max(0.05f, p / 100.0f);
                    txtIntensityLabel.setText(String.format("Pass Intensity: %d%%", (int)(pass.intensity * 100)));
                    refreshStackCardsUI();
                    recalculatePreview();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Dry/Wet Morph Slider
        seekDryWet.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                dryWetRatio = p / 100.0f;
                txtDryWetLabel.setText(p == 100 ? "100% Wet" : (p == 0 ? "100% Dry" : (p + "% Wet")));
                recalculatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Dynamic Parameter Sliders
        seekParam1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                TransformRecipeItem pass = getActivePass();
                if (pass != null) {
                    pass.param1 = p;
                    updateParamLabels();
                    recalculatePreview();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekParam2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                TransformRecipeItem pass = getActivePass();
                if (pass != null) {
                    pass.param2 = p;
                    updateParamLabels();
                    recalculatePreview();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Lock Mask Toggles
        bindLockToggle(btnLockDownbeats, "Downbeat", () -> lockMasks.lockDownbeats = !lockMasks.lockDownbeats);
        bindLockToggle(btnLockPitches, "Pitch", () -> lockMasks.lockPitches = !lockMasks.lockPitches);
        bindLockToggle(btnLockRhythm, "Rhythm", () -> lockMasks.lockRhythm = !lockMasks.lockRhythm);
        bindLockToggle(btnLockVelocities, "Velocity", () -> lockMasks.lockVelocities = !lockMasks.lockVelocities);
        bindLockToggle(btnLockBass, "Bass", () -> lockMasks.lockBassNotes = !lockMasks.lockBassNotes);

        // Actions
                Button btnToggleGhost = findViewById(R.id.btnToggleGhost);
        Button btnAbCompare = findViewById(R.id.btnAbCompare);

        if (btnToggleGhost != null) {
            btnToggleGhost.setOnClickListener(v -> {
                isGhostEnabled = !isGhostEnabled;
                btnToggleGhost.setText(isGhostEnabled ? "👁 GHOST: ON" : "👁 GHOST: OFF");
                btnToggleGhost.setBackgroundColor(isGhostEnabled ? Color.parseColor("#163824") : Color.parseColor("#242734"));
                btnToggleGhost.setTextColor(isGhostEnabled ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
                recalculatePreview();
            });
        }

        if (btnAbCompare != null) {
            btnAbCompare.setOnClickListener(v -> {
                isAbComparingOriginal = !isAbComparingOriginal;
                btnAbCompare.setText(isAbComparingOriginal ? "STATE: A (Orig)" : "A / B");
                btnAbCompare.setBackgroundColor(isAbComparingOriginal ? Color.parseColor("#3D3216") : Color.parseColor("#242734"));
                btnAbCompare.setTextColor(isAbComparingOriginal ? Color.parseColor("#FFD60A") : Color.parseColor("#0A84FF"));
                recalculatePreview();
            });
        }

        btnAuditionPreview.setOnClickListener(v -> toggleAudition(btnAuditionPreview));
        btnCommitTransform.setOnClickListener(v -> commitTransform());
        btnCloseStudio.setOnClickListener(v -> dismiss());

        refreshStackCardsUI();
        syncActivePassToUI();
        switchCategory(0);
    }

    private TransformRecipeItem getActivePass() {
        if (recipeStack.isEmpty()) return null;
        activePassIndex = Math.max(0, Math.min(recipeStack.size() - 1, activePassIndex));
        return recipeStack.get(activePassIndex);
    }

    private void refreshStackCardsUI() {
        if (stackCardsContainer == null) return;
        stackCardsContainer.removeAllViews();

        txtStackHeader.setText(String.format("RECIPE PIPELINE STACK (%d PASS%s)", recipeStack.size(), recipeStack.size() > 1 ? "ES" : ""));

        for (int i = 0; i < recipeStack.size(); i++) {
            final int passIdx = i;
            TransformRecipeItem item = recipeStack.get(i);
            boolean isSelectedPass = (passIdx == activePassIndex);

            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(8, 6, 8, 6);
            card.setBackgroundColor(isSelectedPass ? Color.parseColor("#16385C") : Color.parseColor("#20232E"));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 72);
            lp.setMargins(0, 0, 6, 0);
            card.setLayoutParams(lp);

            // Pass label
            TextView txtCard = new TextView(getContext());
            String status = item.enabled ? "" : " (Muted)";
            txtCard.setText(String.format("P%d: %s%s", (passIdx + 1), getShortOperatorLabel(item.type), status));
            txtCard.setTextColor(isSelectedPass ? Color.WHITE : Color.parseColor("#8E8E93"));
            txtCard.setTextSize(10.0f);
            txtCard.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(txtCard);

            // Toggle enable / mute button
            Button btnToggle = new Button(getContext());
            btnToggle.setText(item.enabled ? "✓" : "–");
            btnToggle.setTextSize(9.0f);
            btnToggle.setBackgroundColor(item.enabled ? Color.parseColor("#163824") : Color.parseColor("#3A3A3C"));
            btnToggle.setTextColor(item.enabled ? Color.parseColor("#30D158") : Color.GRAY);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(54, 54);
            tLp.setMargins(6, 0, 0, 0);
            btnToggle.setLayoutParams(tLp);
            btnToggle.setOnClickListener(v -> {
                item.enabled = !item.enabled;
                refreshStackCardsUI();
                recalculatePreview();
            });
            card.addView(btnToggle);

            // Delete pass button (if more than 1 pass exists)
            if (recipeStack.size() > 1) {
                Button btnDel = new Button(getContext());
                btnDel.setText("✕");
                btnDel.setTextSize(9.0f);
                btnDel.setBackgroundColor(Color.parseColor("#4D1C1E"));
                btnDel.setTextColor(Color.parseColor("#FF453A"));
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(54, 54);
                dLp.setMargins(4, 0, 0, 0);
                btnDel.setLayoutParams(dLp);
                btnDel.setOnClickListener(v -> {
                    recipeStack.remove(passIdx);
                    activePassIndex = Math.max(0, activePassIndex - 1);
                    refreshStackCardsUI();
                    syncActivePassToUI();
                    recalculatePreview();
                });
                card.addView(btnDel);
            }

            card.setOnClickListener(v -> {
                activePassIndex = passIdx;
                refreshStackCardsUI();
                syncActivePassToUI();
            });

            stackCardsContainer.addView(card);
        }
    }

    private String getShortOperatorLabel(TransformRecipeItem.OperatorType type) {
        switch (type) {
            case EUCLIDEAN_SLICE: return "Euclidean";
            case RATCHET_BURST: return "Ratchet";
            case MARKOV_DRIFT: return "Markov";
            case ENCLOSURE_DECORATE: return "Enclosure";
            case MODAL_INVERSION: return "Inversion";
            case DIATONIC_VOICING: return "Voicing";
            case CALL_RESPONSE_INFILL: return "Call&Resp";
            case CLAVE_SLIP: return "Clave";
            case PALINDROME_MIRROR: return "Palindrome";
            case GOLDEN_PHRASE_ARC: return "PhraseArc";
            case HUMANIZE_GROOVE: return "Humanize";
            case SCALE_CONSTRAIN: return "ScaleLock";
            case SCHENKER_LEAD_TOWARD: return "Schenker";
            case BARTOK_PITCH_WEDGE: return "Bartok";
            case COMPOUND_POLY_WEAVE: return "CompoundPoly";
            case DIATONIC_CASCADE_RUN: return "Cascade";
            case CHORD_DROP_VOICING: return "DropVoicing";
            case CONTRARY_COUNTERPOINT: return "Counterpoint";
            case SUB_BASS_EXTRACTOR: return "SubBass";
            case GUITAR_STRUM_PHYSICS: return "Strum";
            case MAQAM_MICROTONAL_BEND: return "MaqamBend";
            case PARABOLIC_VELOCITY_DOME: return "SwellDome";
            default: return "Transform";
        }
    }

    private void syncActivePassToUI() {
        TransformRecipeItem pass = getActivePass();
        if (pass == null) return;

        txtSelectedOperator.setText(String.format("Pass %d: %s", (activePassIndex + 1), pass.type.label));
        seekIntensity.setProgress((int) (pass.intensity * 100));
        txtIntensityLabel.setText(String.format("Pass Intensity: %d%%", (int) (pass.intensity * 100)));
        btnNewSeed.setText("🎲 SEED: #" + pass.seed);
        seekParam1.setProgress((int) pass.param1);
        seekParam2.setProgress((int) pass.param2);
        updateParamLabels();
    }

        private void showSaveStackDialog() {
        Dialog saveDialog = new Dialog(getContext());
        saveDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);

        TextView title = new TextView(getContext());
        title.setText("💾 Save Transformation Stack");
        title.setTextColor(Color.parseColor("#30D158"));
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        android.widget.EditText editName = new android.widget.EditText(getContext());
        editName.setHint("Recipe Preset Name");
        editName.setTextColor(Color.WHITE);
        editName.setHintTextColor(Color.parseColor("#8E8E93"));
        editName.setSingleLine(true);
        layout.addView(editName);

        Button btnSave = new Button(getContext());
        btnSave.setText("Save Stack (.cobasstransform)");
        btnSave.setBackgroundColor(Color.parseColor("#30D158"));
        btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            if (name.isEmpty()) name = "My_Transform_Stack";

            File dir = new File(getContext().getFilesDir(), "presets/transform");
            if (!dir.exists()) dir.mkdirs();

            File targetFile = new File(dir, name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cobasstransform");
            try {
                com.maxica.cobass.model.TransformRecipeSerializer.saveToFile(targetFile, name, recipeStack, lockMasks, dryWetRatio);
                Toast.makeText(getContext(), "Saved Recipe: " + targetFile.getName(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Save Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            saveDialog.dismiss();
        });
        layout.addView(btnSave);

        saveDialog.setContentView(layout);
        if (saveDialog.getWindow() != null) {
            saveDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            saveDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        saveDialog.show();
    }

    private void showMacroPresetsDialog() {
        Dialog macroDialog = new Dialog(getContext());
        macroDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(24, 20, 24, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("📁 Multi-Pass Macro Genre Presets");
        title.setTextColor(Color.parseColor("#FFD60A"));
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("One-tap production chains combining multiple harmonic and rhythmic passes");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 14);
        layout.addView(subTitle);

        addMacroOption(layout, macroDialog, "🔥 Future Bass Chords", "Voicing (Drop-2) ➔ Euclidean 5/8 ➔ Phrase Arc", "Future Bass Chords");
        addMacroOption(layout, macroDialog, "🌪️ Trap Lead Evolution", "Markov Drift ➔ 4x Ratchet ➔ Bebop Enclosure", "Trap Lead Evolution");
        addMacroOption(layout, macroDialog, "🌊 Liquid DnB Roller", "7/16 Euclidean Slice ➔ Clave Slip ➔ Dynamics", "Liquid DnB Roller");
        addMacroOption(layout, macroDialog, "🎹 Neo-Classical Motif", "Palindrome Mirror ➔ 3rds Voicing ➔ Enclosure", "Neo-Classical Motif");
        addMacroOption(layout, macroDialog, "📻 Human Soul Groove", "Organic Humanize ➔ Laid-back Slip ➔ Swell", "Human Soul Groove");
        addMacroOption(layout, macroDialog, "⚡ Cyberpunk Industrial Arp", "Ratchet Rolls ➔ Modal Inversion ➔ Scale Lock", "Cyberpunk Industrial Arp");

                Button btnSaveUserRecipe = new Button(getContext());
        btnSaveUserRecipe.setText("💾 SAVE ACTIVE STACK AS PRESET");
        btnSaveUserRecipe.setBackgroundColor(Color.parseColor("#163824"));
        btnSaveUserRecipe.setTextColor(Color.parseColor("#30D158"));
        btnSaveUserRecipe.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnSaveUserRecipe.setOnClickListener(v -> {
            macroDialog.dismiss();
            showSaveStackDialog();
        });
        layout.addView(btnSaveUserRecipe);

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> macroDialog.dismiss());
        layout.addView(btnCancel);

        macroDialog.setContentView(scroll);
        if (macroDialog.getWindow() != null) {
            macroDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            macroDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        macroDialog.show();
    }

    private void addMacroOption(LinearLayout parent, Dialog dialog, String title, String desc, String macroKey) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(12, 10, 12, 10);
        row.setBackgroundColor(Color.parseColor("#242734"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        row.setLayoutParams(lp);

        TextView t = new TextView(getContext());
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12.0f);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(t);

        TextView d = new TextView(getContext());
        d.setText(desc);
        d.setTextColor(Color.parseColor("#30D158"));
        d.setTextSize(10.0f);
        row.addView(d);

        row.setOnClickListener(v -> {
            int baseSeed = new Random().nextInt(90000) + 10000;
            recipeStack.clear();
            recipeStack.addAll(MidiTransformEngine.getMacroPreset(macroKey, baseSeed));
            activePassIndex = 0;
            refreshStackCardsUI();
            syncActivePassToUI();
            recalculatePreview();
            dialog.dismiss();
            Toast.makeText(getContext(), "⚡ Loaded Macro: " + title, Toast.LENGTH_SHORT).show();
        });

        parent.addView(row);
    }

    private void switchCategory(int catIdx) {
        activeCategory = catIdx;
        Button[] catBtns = {
            findViewById(R.id.btnCatRhythm),
            findViewById(R.id.btnCatMelodic),
            findViewById(R.id.btnCatHarmony),
            findViewById(R.id.btnCatGroove)
        };

        for (int i = 0; i < catBtns.length; i++) {
            if (catBtns[i] != null) {
                boolean isSel = (i == activeCategory);
                catBtns[i].setBackgroundColor(isSel ? Color.parseColor("#16385C") : Color.parseColor("#242734"));
                catBtns[i].setTextColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#8E8E93"));
            }
        }

        operatorButtonsContainer.removeAllViews();

        if (activeCategory == 0) { // Rhythm
            addOperatorChoice("Euclidean Rhythmic Slicer", TransformRecipeItem.OperatorType.EUCLIDEAN_SLICE, 8, 5);
            addOperatorChoice("Accelerating Ratchet Burst", TransformRecipeItem.OperatorType.RATCHET_BURST, 4, 1);
            addOperatorChoice("Clave Syncopation Slip", TransformRecipeItem.OperatorType.CLAVE_SLIP, 0, 0);
        } else if (activeCategory == 1) { // Melodic
            addOperatorChoice("Markov Melodic Drift", TransformRecipeItem.OperatorType.MARKOV_DRIFT, 0, 0);
            addOperatorChoice("Chromatic Enclosure Decorate", TransformRecipeItem.OperatorType.ENCLOSURE_DECORATE, 0, 0);
            addOperatorChoice("Schenkerian Cadence Lead", TransformRecipeItem.OperatorType.SCHENKER_LEAD_TOWARD, 0, 0);
            addOperatorChoice("Bartók Pitch Wedge", TransformRecipeItem.OperatorType.BARTOK_PITCH_WEDGE, 60, 1);
            addOperatorChoice("Modal Axis Inversion", TransformRecipeItem.OperatorType.MODAL_INVERSION, 60, 0);
            addOperatorChoice("Scale Tone Constrain", TransformRecipeItem.OperatorType.SCALE_CONSTRAIN, 0, 0);
        } else if (activeCategory == 2) { // Harmony
            addOperatorChoice("Drop-2/3 Voicing Spreader", TransformRecipeItem.OperatorType.CHORD_DROP_VOICING, 0, 0);
            addOperatorChoice("Contrary Counterpoint", TransformRecipeItem.OperatorType.CONTRARY_COUNTERPOINT, 0, 0);
            addOperatorChoice("Sub-Bass Root Extractor", TransformRecipeItem.OperatorType.SUB_BASS_EXTRACTOR, 0, 0);
            addOperatorChoice("Diatonic Voicing (3rds/6ths)", TransformRecipeItem.OperatorType.DIATONIC_VOICING, 2, 0);
            addOperatorChoice("Compound Polyphony Weave", TransformRecipeItem.OperatorType.COMPOUND_POLY_WEAVE, 0, 0);
            addOperatorChoice("Diatonic Cascade Run", TransformRecipeItem.OperatorType.DIATONIC_CASCADE_RUN, 0, 0);
            addOperatorChoice("Call & Response Infill", TransformRecipeItem.OperatorType.CALL_RESPONSE_INFILL, 0, 0);
            addOperatorChoice("Palindrome Reflection", TransformRecipeItem.OperatorType.PALINDROME_MIRROR, 0, 0);
        } else { // Groove
            addOperatorChoice("Acoustic Guitar Strum", TransformRecipeItem.OperatorType.GUITAR_STRUM_PHYSICS, 1, 20);
            addOperatorChoice("Parabolic Dynamics Swell", TransformRecipeItem.OperatorType.PARABOLIC_VELOCITY_DOME, 40, 95);
            addOperatorChoice("Maqam / Blues Inflector", TransformRecipeItem.OperatorType.MAQAM_MICROTONAL_BEND, 0, 0);
            addOperatorChoice("Organic Humanize Groove", TransformRecipeItem.OperatorType.HUMANIZE_GROOVE, 0, 0);
            addOperatorChoice("Golden Ratio Dynamics Arc", TransformRecipeItem.OperatorType.GOLDEN_PHRASE_ARC, 0, 0);
        }
    }

    private void addOperatorChoice(String label, TransformRecipeItem.OperatorType type, float defaultP1, float defaultP2) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setTextSize(10.0f);

        TransformRecipeItem pass = getActivePass();
        boolean isSel = (pass != null && pass.type == type);
        btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#20232E"));
        btn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 76);
        lp.setMargins(0, 3, 0, 3);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            TransformRecipeItem p = getActivePass();
            if (p != null) {
                p.type = type;
                p.param1 = defaultP1;
                p.param2 = defaultP2;
                txtSelectedOperator.setText(String.format("Pass %d: %s", (activePassIndex + 1), label));
                switchCategory(activeCategory);
                refreshStackCardsUI();
                updateParamLabels();
                recalculatePreview();
            }
        });

        operatorButtonsContainer.addView(btn);
    }

    private void updateParamLabels() {
        TransformRecipeItem pass = getActivePass();
        if (pass == null) return;

        if (pass.type == TransformRecipeItem.OperatorType.EUCLIDEAN_SLICE) {
            txtParam1Label.setText("Steps (N): " + (int) pass.param1);
            txtParam2Label.setText("Pulses (K): " + (int) pass.param2);
        } else if (pass.type == TransformRecipeItem.OperatorType.RATCHET_BURST) {
            txtParam1Label.setText("Subdivisions: " + (int) pass.param1 + " rolls");
            txtParam2Label.setText("Direction: " + (pass.param2 >= 0 ? "Accelerating" : "Decelerating"));
        } else if (pass.type == TransformRecipeItem.OperatorType.DIATONIC_VOICING) {
            txtParam1Label.setText("Diatonic Degree Shift: +" + (int) pass.param1);
            txtParam2Label.setText("Voicing Mode: " + ((int) pass.param2 == 0 ? "Close" : ((int) pass.param2 == 1 ? "Drop-2" : "Open 10ths")));
        } else if (pass.type == TransformRecipeItem.OperatorType.MODAL_INVERSION) {
            txtParam1Label.setText("Inversion Axis MIDI: " + (int) pass.param1);
            txtParam2Label.setText("Axis Tone: C4 (Center)");
        } else {
            txtParam1Label.setText("Parameter 1: " + (int) pass.param1);
            txtParam2Label.setText("Parameter 2: " + (int) pass.param2);
        }
    }

    private void bindLockToggle(Button btn, String label, Runnable toggleAction) {
        btn.setOnClickListener(v -> {
            toggleAction.run();
            boolean active = btn.getText().toString().contains("✓");
            btn.setText(!active ? ("✓ " + label) : ("🔒 " + label));
            btn.setBackgroundColor(!active ? Color.parseColor("#163824") : Color.parseColor("#242734"));
            btn.setTextColor(!active ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
            recalculatePreview();
        });
    }

    private void recalculatePreview() {
        if (singleClip == null || singleClip.getNotes().isEmpty()) return;

        if (isAbComparingOriginal) {
            previewNotes = singleClip.cloneNotesList();
        } else {
            previewNotes = MidiTransformEngine.previewPipeline(
                singleClip.getNotes(), scale, rootKey, recipeStack, lockMasks, dryWetRatio
            );
        }

        if (livePreviewListener != null) {
            livePreviewListener.onLiveNotesPreview(isGhostEnabled ? previewNotes : Collections.emptyList());
        }
    }

    private void toggleAudition(Button btnAudition) {
        if (!AudioEngineNative.isLoaded() || singleClip == null) return;
        isAuditioning = !isAuditioning;

        if (isAuditioning) {
            btnAudition.setText("■ STOP PREVIEW");
            btnAudition.setBackgroundColor(Color.parseColor("#4D1C1E"));
            btnAudition.setTextColor(Color.parseColor("#FF453A"));
            AudioEngineNative.nativeNoteOn(singleClip.getTrackId(), 60, 0.9f);
        } else {
            btnAudition.setText("▶ AUDITION");
            btnAudition.setBackgroundColor(Color.parseColor("#163824"));
            btnAudition.setTextColor(Color.parseColor("#30D158"));
            AudioEngineNative.nativeNoteOff(singleClip.getTrackId(), 60);
        }
    }

    private void commitTransform() {
        if (targetClips.size() > 1) {
            int count = MidiTransformEngine.applyPipelineBatch(targetClips, scale, rootKey, recipeStack, lockMasks, dryWetRatio);
            Toast.makeText(getContext(), "⚡ Transformed " + count + " clips with " + recipeStack.size() + " stacked passes", Toast.LENGTH_SHORT).show();
        } else if (singleClip != null) {
            boolean selOnly = !singleClip.getSelectedNotes().isEmpty();
            MidiTransformEngine.applyPipeline(singleClip, scale, rootKey, recipeStack, lockMasks, dryWetRatio, selOnly);
            Toast.makeText(getContext(), "⚡ Applied " + recipeStack.size() + " transformation passes", Toast.LENGTH_SHORT).show();
        }

        if (commitListener != null) {
            commitListener.onTransformCommitted();
        }
        dismiss();
    }

    @Override
    public void dismiss() {
        if (isAuditioning && singleClip != null && AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeNoteOff(singleClip.getTrackId(), 60);
            isAuditioning = false;
        }
        if (livePreviewListener != null) {
            livePreviewListener.onLiveNotesPreview(Collections.emptyList());
        }
        super.dismiss();
    }
}
