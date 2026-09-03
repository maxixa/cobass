package com.maxica.cobass.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TransformRecipeSerializer {

    private TransformRecipeSerializer() {}

    public static String serializeStack(String recipeName, List<TransformRecipeItem> stack, TransformLockMasks masks, float dryWet) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("name", recipeName != null ? recipeName : "Custom Recipe");
        root.put("dryWet", (double) dryWet);

        if (masks != null) {
            JSONObject mObj = new JSONObject();
            mObj.put("lockDownbeats", masks.lockDownbeats);
            mObj.put("lockPitches", masks.lockPitches);
            mObj.put("lockRhythm", masks.lockRhythm);
            mObj.put("lockVelocities", masks.lockVelocities);
            mObj.put("lockBassNotes", masks.lockBassNotes);
            root.put("lockMasks", mObj);
        }

        JSONArray arr = new JSONArray();
        if (stack != null) {
            for (TransformRecipeItem item : stack) {
                JSONObject rObj = new JSONObject();
                rObj.put("type", item.type.name());
                rObj.put("intensity", (double) item.intensity);
                rObj.put("seed", item.seed);
                rObj.put("param1", (double) item.param1);
                rObj.put("param2", (double) item.param2);
                rObj.put("enabled", item.enabled);
                arr.put(rObj);
            }
        }
        root.put("recipes", arr);

        return root.toString(2);
    }

    public static List<TransformRecipeItem> deserializeStack(String jsonStr) throws Exception {
        List<TransformRecipeItem> stack = new ArrayList<>();
        JSONObject root = new JSONObject(jsonStr);
        JSONArray arr = root.optJSONArray("recipes");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rObj = arr.getJSONObject(i);
                TransformRecipeItem.OperatorType type;
                try {
                    type = TransformRecipeItem.OperatorType.valueOf(rObj.getString("type"));
                } catch (Exception ignored) {
                    type = TransformRecipeItem.OperatorType.SCALE_CONSTRAIN;
                }
                float intensity = (float) rObj.optDouble("intensity", 0.5);
                int seed = rObj.optInt("seed", 12345);
                float param1 = (float) rObj.optDouble("param1", 0.0);
                float param2 = (float) rObj.optDouble("param2", 0.0);
                boolean enabled = rObj.optBoolean("enabled", true);

                TransformRecipeItem item = new TransformRecipeItem(type, intensity, seed, param1, param2);
                item.enabled = enabled;
                stack.add(item);
            }
        }
        return stack;
    }

    public static void saveToFile(File file, String name, List<TransformRecipeItem> stack, TransformLockMasks masks, float dryWet) throws Exception {
        String json = serializeStack(name, stack, masks, dryWet);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    public static List<TransformRecipeItem> loadFromFile(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int read = fis.read(buf);
            if (read > 0) {
                return deserializeStack(new String(buf, 0, read, StandardCharsets.UTF_8));
            }
        }
        return new ArrayList<>();
    }
}
