package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
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

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        List<PluginDescriptorItem> effects = PluginHostManager.getInstance().getEffectPlugins();
        if (effects.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No modular FX plugins discovered in app library.");
            CobassTypography.applyBody(empty);
            empty.setPadding(0, Math.round(CobassSpacing.SPACE_MD * density), 0, Math.round(CobassSpacing.SPACE_MD * density));
            content.addView(empty);
        } else {
            for (PluginDescriptorItem fx : effects) {
                Button btn = new Button(getContext());
                btn.setText(fx.getName() + " (" + fx.getVendor() + ")");
                CobassButton.apply(btn, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, Math.round(2 * density), 0, Math.round(2 * density));
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> {
                    if (listener != null) listener.onFxSelected(fx);
                    dismiss();
                });
                content.addView(btn);
            }
        }

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🎛 Insert FX Plugin Browser",
            "Assign modular DSP plugin to Slot " + (slotIndex + 1),
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }
}
