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

public class FxPluginBrowserDialog extends Dialog {

    public interface OnFxSelectedListener {
        void onFxSelected(PluginDescriptorItem fxPlugin);
    }

    private final int slotIndex;
    private final OnFxSelectedListener listener;

    public FxPluginBrowserDialog(@NonNull Context context, int slotIndex, OnFxSelectedListener listener) {
        super(context);
        this.slotIndex = slotIndex;
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
        title.setText("Insert FX Plugin Browser (Slot " + (slotIndex + 1) + ")");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        List<PluginDescriptorItem> effects = PluginHostManager.getInstance().getEffectPlugins();
        if (effects.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No modular FX plugins discovered in app library.");
            empty.setTextColor(Color.parseColor("#8E8E93"));
            empty.setPadding(0, 16, 0, 16);
            layout.addView(empty);
        } else {
            for (PluginDescriptorItem fx : effects) {
                Button btn = new Button(getContext());
                btn.setText(fx.getName() + " (" + fx.getVendor() + ")");
                btn.setTextSize(11f);
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(Color.parseColor("#242734"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 4, 0, 4);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> {
                    if (listener != null) listener.onFxSelected(fx);
                    dismiss();
                });
                layout.addView(btn);
            }
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
