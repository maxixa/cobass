package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.project.ProjectData;
import com.maxica.cobass.project.ProjectSerializer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProjectDialog extends Dialog {

    public interface OnProjectActionListener {
        ProjectData getCurrentProjectData();
        void onProjectLoaded(ProjectData data);
        void onNewProjectTemplate();
    }

    private final OnProjectActionListener listener;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isExporting = false;

    public ProjectDialog(@NonNull Context context, OnProjectActionListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_project_manager);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText editProjectName = findViewById(R.id.editProjectName);
        Button btnSaveProject = findViewById(R.id.btnSaveProject);
        Button btnNewProject = findViewById(R.id.btnNewProject);
        Button btnExportWav = findViewById(R.id.btnExportWav);
        Button btnClose = findViewById(R.id.btnCloseProjectDialog);

        ProgressBar exportProgress = findViewById(R.id.exportProgressBar);
        TextView txtExportStatus = findViewById(R.id.txtExportStatus);
        LinearLayout projectListContainer = findViewById(R.id.projectListContainer);

        ProjectData current = listener.getCurrentProjectData();
        if (current != null) {
            editProjectName.setText(current.projectName);
        }

        File projectsDir = new File(getContext().getFilesDir(), "projects");
        if (!projectsDir.exists()) projectsDir.mkdirs();

        // 1. Populate Saved Projects List
        refreshProjectList(projectListContainer, projectsDir);

        // 2. Save Project Action
        btnSaveProject.setOnClickListener(v -> {
            String name = editProjectName.getText().toString().trim();
            if (name.isEmpty()) name = "Cobass_Song";

            ProjectData data = listener.getCurrentProjectData();
            data.projectName = name;

            File targetFile = new File(projectsDir, name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cobass");
            try {
                ProjectSerializer.saveToFile(targetFile, data);
                Toast.makeText(getContext(), "Project Saved: " + targetFile.getName(), Toast.LENGTH_SHORT).show();
                refreshProjectList(projectListContainer, projectsDir);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Save Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // 3. New Project Action
        btnNewProject.setOnClickListener(v -> {
            listener.onNewProjectTemplate();
            dismiss();
            Toast.makeText(getContext(), "New Template Loaded", Toast.LENGTH_SHORT).show();
        });

        // 4. High-Speed Offline Audio Export Action
        btnExportWav.setOnClickListener(v -> {
            if (isExporting) return;

            File exportsDir = new File(getContext().getExternalFilesDir(null), "Exports");
            if (!exportsDir.exists()) exportsDir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = editProjectName.getText().toString().replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + timeStamp + ".wav";
            File outputFile = new File(exportsDir, fileName);

            exportProgress.setVisibility(View.VISIBLE);
            txtExportStatus.setVisibility(View.VISIBLE);
            txtExportStatus.setText("Rendering Master WAV (Fast Offline Bounce)...");
            btnExportWav.setEnabled(false);
            isExporting = true;

            // Calculate total project ticks from clips
            long maxTick = 1920 * 4; // Default 4 bars
            ProjectData pData = listener.getCurrentProjectData();
            for (var c : pData.clips) {
                maxTick = Math.max(maxTick, c.getEndTick() + 480);
            }

            final long totalTicks = maxTick;
            final float sampleRate = 48000.0f;

            new Thread(() -> {
                boolean success = AudioEngineNative.nativeExportWav(outputFile.getAbsolutePath(), sampleRate, totalTicks);
                progressHandler.post(() -> {
                    isExporting = false;
                    exportProgress.setVisibility(View.GONE);
                    btnExportWav.setEnabled(true);

                    if (success && outputFile.exists()) {
                        txtExportStatus.setText("Export Complete: " + outputFile.getName() + " (" + (outputFile.length() / 1024) + " KB)");
                        Toast.makeText(getContext(), "WAV Exported Successfully!", Toast.LENGTH_SHORT).show();
                        shareExportedWav(outputFile);
                    } else {
                        txtExportStatus.setText("Export Failed.");
                        Toast.makeText(getContext(), "Export Failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();

            // Progress Poll Loop
            progressHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isExporting) {
                        float prog = AudioEngineNative.nativeGetExportProgress();
                        exportProgress.setProgress((int) (prog * 100));
                        txtExportStatus.setText(String.format(Locale.US, "Bouncing Master WAV: %d%%", (int) (prog * 100)));
                        progressHandler.postDelayed(this, 50);
                    }
                }
            });
        });

        btnClose.setOnClickListener(v -> dismiss());
    }

    private void refreshProjectList(LinearLayout container, File dir) {
        container.removeAllViews();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".cobass"));
        if (files == null || files.length == 0) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No saved projects found");
            emptyText.setTextColor(Color.parseColor("#8E8E93"));
            emptyText.setPadding(0, 10, 0, 10);
            container.addView(emptyText);
            return;
        }

        for (File f : files) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView txtName = new TextView(getContext());
            txtName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            txtName.setText(f.getName().replace(".cobass", ""));
            txtName.setTextColor(Color.WHITE);
            txtName.setTextSize(14f);

            Button btnOpen = new Button(getContext());
            btnOpen.setText("OPEN");
            btnOpen.setTextSize(11f);
            btnOpen.setBackgroundColor(Color.parseColor("#0A84FF"));
            btnOpen.setOnClickListener(v -> {
                try {
                    ProjectData loaded = ProjectSerializer.loadFromFile(f);
                    listener.onProjectLoaded(loaded);
                    dismiss();
                    Toast.makeText(getContext(), "Opened: " + f.getName(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Load Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            Button btnDelete = new Button(getContext());
            btnDelete.setText("✕");
            btnDelete.setTextSize(11f);
            btnDelete.setBackgroundColor(Color.parseColor("#FF453A"));
            btnDelete.setOnClickListener(v -> {
                f.delete();
                refreshProjectList(container, dir);
            });

            row.addView(txtName);
            row.addView(btnOpen);
            row.addView(btnDelete);
            container.addView(row);
        }
    }

    private void shareExportedWav(File wavFile) {
        try {
            Uri contentUri = FileProvider.getUriForFile(getContext(), "com.maxica.cobass.fileprovider", wavFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/wav");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(Intent.createChooser(shareIntent, "Share Cobass Master WAV"));
        } catch (Exception ignored) {}
    }
}
