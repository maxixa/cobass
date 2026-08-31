package com.maxica.cobass.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.maxica.cobass.model.PluginParamItem;

public final class PluginControlFactory {

    public interface OnParamChangeListener {
        void onParamChanged(PluginParamItem param, float value);
    }

    private PluginControlFactory() {}

    public static RotaryKnobView createRotaryKnob(Context context, PluginParamItem param, float initialValue, OnParamChangeListener listener) {
        RotaryKnobView knob = new RotaryKnobView(context);
        knob.setParamItem(param);
        knob.setValue(initialValue, false);
        knob.setOnKnobChangeListener((k, value, fromUser) -> {
            if (fromUser && listener != null) {
                listener.onParamChanged(param, value);
            }
        });
        return knob;
    }

    public static View createBooleanToggle(Context context, PluginParamItem param, boolean initialValue, OnParamChangeListener listener) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(4, 12, 4, 12);

        TextView lbl = new TextView(context);
        lbl.setText(param.getName());
        lbl.setTextColor(Color.parseColor("#8E8E93"));
        lbl.setTextSize(11f);
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        lbl.setGravity(Gravity.CENTER);
        box.addView(lbl);

        Button btnToggle = new Button(context);
        btnToggle.setTextSize(11f);
        btnToggle.setTypeface(null, android.graphics.Typeface.BOLD);
        updateBoolButton(btnToggle, initialValue);

        btnToggle.setOnClickListener(v -> {
            boolean current = btnToggle.getText().toString().equals("ON");
            boolean next = !current;
            updateBoolButton(btnToggle, next);
            if (listener != null) {
                listener.onParamChanged(param, next ? 1.0f : 0.0f);
            }
        });

        box.addView(btnToggle);
        return box;
    }

    public static View createChoiceStepper(Context context, PluginParamItem param, int initialValue, OnParamChangeListener listener) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(4, 12, 4, 12);

        TextView lbl = new TextView(context);
        lbl.setText(param.getName());
        lbl.setTextColor(Color.parseColor("#8E8E93"));
        lbl.setTextSize(11f);
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        lbl.setGravity(Gravity.CENTER);
        box.addView(lbl);

        LinearLayout rowStep = new LinearLayout(context);
        rowStep.setOrientation(LinearLayout.HORIZONTAL);
        rowStep.setGravity(Gravity.CENTER);

        Button btnMinus = new Button(context);
        btnMinus.setText("◀");
        btnMinus.setTextSize(10f);
        btnMinus.setBackgroundColor(Color.parseColor("#242734"));
        btnMinus.setTextColor(Color.WHITE);
        btnMinus.setLayoutParams(new LinearLayout.LayoutParams(60, 80));

        TextView txtVal = new TextView(context);
        txtVal.setTextSize(12f);
        txtVal.setTextColor(Color.WHITE);
        txtVal.setTypeface(null, android.graphics.Typeface.BOLD);
        txtVal.setGravity(Gravity.CENTER);
        txtVal.setPadding(8, 0, 8, 0);

        Button btnPlus = new Button(context);
        btnPlus.setText("▶");
        btnPlus.setTextSize(10f);
        btnPlus.setBackgroundColor(Color.parseColor("#242734"));
        btnPlus.setTextColor(Color.WHITE);
        btnPlus.setLayoutParams(new LinearLayout.LayoutParams(60, 80));

        updateChoiceLabel(txtVal, param, initialValue);

        final int[] valHolder = {initialValue};
        btnMinus.setOnClickListener(v -> {
            if (valHolder[0] > (int) param.getMinValue()) {
                valHolder[0]--;
                updateChoiceLabel(txtVal, param, valHolder[0]);
                if (listener != null) listener.onParamChanged(param, (float) valHolder[0]);
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (valHolder[0] < (int) param.getMaxValue()) {
                valHolder[0]++;
                updateChoiceLabel(txtVal, param, valHolder[0]);
                if (listener != null) listener.onParamChanged(param, (float) valHolder[0]);
            }
        });

        rowStep.addView(btnMinus);
        rowStep.addView(txtVal);
        rowStep.addView(btnPlus);
        box.addView(rowStep);
        return box;
    }

    private static void updateBoolButton(Button btn, boolean state) {
        btn.setText(state ? "ON" : "OFF");
        btn.setTextColor(state ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
        btn.setBackgroundColor(state ? Color.parseColor("#163824") : Color.parseColor("#242734"));
    }

    public static void updateChoiceLabel(TextView txt, PluginParamItem param, int val) {
        if (param.getType() == PluginParamItem.Type.CHOICE && !param.getChoices().isEmpty()) {
            int idx = Math.max(0, Math.min(param.getChoices().size() - 1, val));
            txt.setText(param.getChoices().get(idx));
        } else {
            txt.setText(String.valueOf(val));
        }
    }
}
