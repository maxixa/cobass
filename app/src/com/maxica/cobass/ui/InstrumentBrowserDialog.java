package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
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

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        Button btnDefault = new Button(getContext());
        btnDefault.setText("Cobass PolySynth (Internal Default)");
        CobassButton.apply(btnDefault, CobassButton.Variant.PRIMARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams dfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dfLp.setMargins(0, 0, 0, Math.round(CobassSpacing.SPACE_SM * density));
        btnDefault.setLayoutParams(dfLp);
        btnDefault.setOnClickListener(v -> {
            if (listener != null) listener.onDefaultInstrumentSelected();
            dismiss();
        });
        content.addView(btnDefault);

        List<PluginDescriptorItem> synths = PluginHostManager.getInstance().getSynthPlugins();
        for (PluginDescriptorItem synth : synths) {
            Button btn = new Button(getContext());
            btn.setText(synth.getName() + " (" + synth.getVendor() + ")");
            CobassButton.apply(btn, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Math.round(2 * density), 0, Math.round(2 * density));
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onPluginInstrumentSelected(synth);
                dismiss();
            });
            content.addView(btn);
        }

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🎹 Choose Instrument Engine",
            "Select internal synth or modular plugin for track",
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }
}
