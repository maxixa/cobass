package com.maxica.cobass.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.maxica.cobass.R;
import com.maxica.cobass.core.CobassApplication;

public class CrashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        String crashLog = getIntent().getStringExtra(CobassApplication.EXTRA_CRASH_LOG);
        String summary = getIntent().getStringExtra(CobassApplication.EXTRA_CRASH_SUMMARY);

        if (crashLog == null || crashLog.isEmpty()) {
            crashLog = "No crash log details were passed.";
        }
        if (summary == null || summary.isEmpty()) {
            summary = "An unexpected error caused Cobass to terminate.";
        }

        TextView txtCrashSummary = findViewById(R.id.txtCrashSummary);
        TextView txtCrashLog = findViewById(R.id.txtCrashLog);
        Button btnCopyLog = findViewById(R.id.btnCopyLog);
        Button btnRestartApp = findViewById(R.id.btnRestartApp);
        Button btnCloseCrash = findViewById(R.id.btnCloseCrash);

        txtCrashSummary.setText(summary);
        txtCrashLog.setText(crashLog);

        final String finalLog = crashLog;
        btnCopyLog.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Cobass Crash Log", finalLog);
                cm.setPrimaryClip(clip);
                Toast.makeText(this, "✓ Crash log copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        btnRestartApp.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Process.killProcess(Process.myPid());
        });

        btnCloseCrash.setOnClickListener(v -> {
            finish();
            Process.killProcess(Process.myPid());
            System.exit(0);
        });
    }

    @Override
    public void onBackPressed() {
        finish();
        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}
