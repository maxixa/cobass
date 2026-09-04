package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        File presetDir = new File(getContext().getFilesDir(), "presets/" + descriptor.getPluginId());
        if (!presetDir.exists()) presetDir.mkdirs();

        refreshPresets(content, presetDir);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "📁 Preset Library: " + descriptor.getName(),
            "Load factory presets or custom user patch state",
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }

    private void refreshPresets(LinearLayout container, File presetDir) {
        container.removeAllViews();
        float density = getContext().getResources().getDisplayMetrics().density;
        File[] files = presetDir.listFiles((d, name) -> name.endsWith(".cobasspatch"));
        if (files == null || files.length == 0) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No saved user patches found. Click SAVE in toolbar to create one.");
            CobassTypography.applyBody(emptyText);
            emptyText.setPadding(0, Math.round(CobassSpacing.SPACE_MD * density), 0, Math.round(CobassSpacing.SPACE_MD * density));
            container.addView(emptyText);
            return;
        }

        for (File f : files) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Math.round(4 * density), 0, Math.round(4 * density));

            TextView txtName = new TextView(getContext());
            txtName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            txtName.setText(f.getName().replace(".cobasspatch", ""));
            CobassTypography.applyLabel(txtName);

            Button btnLoad = new Button(getContext());
            btnLoad.setText("LOAD");
            CobassButton.apply(btnLoad, CobassButton.Variant.PRIMARY, CobassButton.Size.COMPACT);
            btnLoad.setOnClickListener(v -> {
                loadPatchFromFile(f);
                dismiss();
            });

            Button btnDel = new Button(getContext());
            btnDel.setText("✕");
            CobassButton.apply(btnDel, CobassButton.Variant.DANGER, CobassButton.Size.COMPACT);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                Math.round(CobassSpacing.BTN_HEIGHT_COMPACT * density),
                Math.round(CobassSpacing.BTN_HEIGHT_COMPACT * density)
            );
            dLp.leftMargin = Math.round(4 * density);
            btnDel.setLayoutParams(dLp);
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

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText editName = new EditText(context);
        editName.setHint("Patch Name");
        editName.setTextColor(CobassTheme.TEXT_PRIMARY);
        editName.setBackgroundColor(CobassTheme.SURFACE_0);
        editName.setHintTextColor(CobassTheme.TEXT_DISABLED);
        int pad = Math.round(8 * density);
        editName.setPadding(pad, pad, pad, pad);
        editName.setSingleLine(true);
        content.addView(editName);

        Button btnSave = new Button(context);
        btnSave.setText("Save Preset (.cobasspatch)");
        CobassButton.apply(btnSave, CobassButton.Variant.SUCCESS, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = Math.round(CobassSpacing.SPACE_MD * density);
        btnSave.setLayoutParams(sLp);
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
        content.addView(btnSave);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            context,
            "💾 Save User Patch",
            "Store current parameter settings into preset archive",
            content,
            v -> dialog.dismiss()
        );

        dialog.setContentView(root);
        CobassDialogShell.configureWindow(dialog);
        dialog.show();
    }
}
