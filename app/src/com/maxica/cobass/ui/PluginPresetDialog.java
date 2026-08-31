package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.model.PluginDescriptorItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class PluginPresetDialog extends Dialog {

    public interface OnPresetActionListener {
        String onGetPluginStateJson();
        void onSetPluginStateJson(String jsonState);
    }

    private final PluginDescriptorItem descriptor;
    private final OnPresetActionListener listener;

    public PluginPresetDialog(@NonNull Context context, PluginDescriptorItem descriptor, OnPresetActionListener listener) {
        super(context);
        this.descriptor = descriptor;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

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

        LinearLayout listContainer = new LinearLayout(getContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        layout.addView(listContainer);

        File presetDir = new File(getContext().getFilesDir(), "presets/" + descriptor.getPluginId());
        if (!presetDir.exists()) presetDir.mkdirs();

        refreshPresets(listContainer, presetDir);

        Button btnDone = new Button(getContext());
        btnDone.setText("Close");
        btnDone.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dismiss());
        layout.addView(btnDone);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void refreshPresets(LinearLayout container, File presetDir) {
        container.removeAllViews();
        File[] files = presetDir.listFiles((d, name) -> name.endsWith(".cobasspatch"));
        if (files == null || files.length == 0) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No saved user patches found. Click SAVE in toolbar to create one.");
            emptyText.setTextColor(Color.parseColor("#8E8E93"));
            emptyText.setPadding(0, 16, 0, 16);
            container.addView(emptyText);
            return;
        }

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
                dismiss();
            });

            Button btnDel = new Button(getContext());
            btnDel.setText("✕");
            btnDel.setTextSize(10f);
            btnDel.setBackgroundColor(Color.parseColor("#FF453A"));
            btnDel.setOnClickListener(v -> {
                f.delete();
                refreshPresets(container, presetDir);
            });

            row.addView(txtName);
            row.addView(btnLoad);
            row.addView(btnDel);
            container.addView(row);
        }
    }

    private void loadPatchFromFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int read = fis.read(buf);
            if (read > 0) {
                String json = new String(buf, 0, read, StandardCharsets.UTF_8);
                if (listener != null) listener.onSetPluginStateJson(json);
                Toast.makeText(getContext(), "Loaded: " + file.getName(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Load Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void showSaveDialog(Context context, PluginDescriptorItem descriptor, OnPresetActionListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);

        TextView title = new TextView(context);
        title.setText("💾 Save User Patch Preset");
        title.setTextColor(Color.parseColor("#30D158"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        EditText editName = new EditText(context);
        editName.setHint("Patch Name");
        editName.setTextColor(Color.WHITE);
        editName.setHintTextColor(Color.parseColor("#8E8E93"));
        editName.setSingleLine(true);
        layout.addView(editName);

        Button btnSave = new Button(context);
        btnSave.setText("Save Preset (.cobasspatch)");
        btnSave.setBackgroundColor(Color.parseColor("#30D158"));
        btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            if (name.isEmpty()) name = "User_Patch";

            File presetDir = new File(context.getFilesDir(), "presets/" + descriptor.getPluginId());
            if (!presetDir.exists()) presetDir.mkdirs();

            File targetFile = new File(presetDir, name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cobasspatch");
            if (listener != null) {
                String json = listener.onGetPluginStateJson();
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                    Toast.makeText(context, "Saved: " + targetFile.getName(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Save Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
        });
        layout.addView(btnSave);

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }
}
