package com.maxica.cobass.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginParamItem {
    public enum Type {
        FLOAT,
        INT,
        BOOL,
        CHOICE
    }

    private final int id;
    private final String name;
    private final String label;
    private final Type type;
    private final float minValue;
    private final float maxValue;
    private final float defaultValue;
    private final float step;
    private final boolean isLogarithmic;
    private final List<String> choices = new ArrayList<>();

    public PluginParamItem(int id, String name, String label, int typeOrdinal,
                           float minValue, float maxValue, float defaultValue,
                           float step, boolean isLogarithmic) {
        this.id = id;
        this.name = name != null ? name : "";
        this.label = label != null ? label : "";
        Type[] types = Type.values();
        this.type = (typeOrdinal >= 0 && typeOrdinal < types.length) ? types[typeOrdinal] : Type.FLOAT;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
        this.step = step;
        this.isLogarithmic = isLogarithmic;
    }

    public void addChoice(String choice) {
        if (choice != null) {
            choices.add(choice);
        }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getLabel() { return label; }
    public Type getType() { return type; }
    public float getMinValue() { return minValue; }
    public float getMaxValue() { return maxValue; }
    public float getDefaultValue() { return defaultValue; }
    public float getStep() { return step; }
    public boolean isLogarithmic() { return isLogarithmic; }
    public List<String> getChoices() { return Collections.unmodifiableList(choices); }
}
