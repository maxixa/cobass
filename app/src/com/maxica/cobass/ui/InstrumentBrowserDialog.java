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
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.plugin.PluginHostManager;

import java.util.List;

public class InstrumentBrowserDialog extends Dialog {

    public interface OnInstrumentSelectedListener {
        void onDefaultInstrumentSelected();
        void onPluginInstrumentSelected(PluginDescriptorItem plugin);
    }

    private final OnInstrumentSelectedListener listener;

    public InstrumentBrowserDialog(@NonNull Context context, OnInstrumentSelectedListener listener) {
        super(context);
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
        title.setText("Choose Instrument Engine");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        Button btnDefault = new Button(getContext());
        btnDefault.setText("Cobass PolySynth (Internal Default)");
        btnDefault.setTextSize(11f);
        btnDefault.setTextColor(Color.WHITE);
        btnDefault.setBackgroundColor(Color.parseColor("#16385C"));
        btnDefault.setOnClickListener(v -> {
            if (listener != null) listener.onDefaultInstrumentSelected();
            dismiss();
        });
        layout.addView(btnDefault);

        List<PluginDescriptorItem> synths = PluginHostManager.getInstance().getSynthPlugins();
        for (PluginDescriptorItem synth : synths) {
            Button btn = new Button(getContext());
            btn.setText(synth.getName() + " (" + synth.getVendor() + ")");
            btn.setTextSize(11f);
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundColor(Color.parseColor("#242734"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 6, 0, 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onPluginInstrumentSelected(synth);
                dismiss();
            });
            layout.addView(btn);
        }

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> dismiss());
        layout.addView(btnCancel);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
