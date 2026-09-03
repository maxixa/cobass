package com.maxica.cobass.sequencer;

import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.MusicalScale;
import com.maxica.cobass.model.TransformLockMasks;
import com.maxica.cobass.model.TransformRecipeItem;

import java.util.ArrayList;
import java.util.List;

public final class NoteTransformPipeline {

    private NoteTransformPipeline() {}

    public static List<ClipItem.Note> execute(
        List<ClipItem.Note> inputNotes,
        MusicalScale scale,
        int rootKey,
        List<TransformRecipeItem> recipeStack,
        TransformLockMasks masks,
        float dryWetRatio
    ) {
        if (inputNotes == null || inputNotes.isEmpty()) return new ArrayList<>();
        if (recipeStack == null || recipeStack.isEmpty()) {
            List<ClipItem.Note> passthrough = new ArrayList<>();
            for (ClipItem.Note n : inputNotes) passthrough.add(n.copy());
            return passthrough;
        }

        // Pack Note list into long[]: [pitch, Float.floatToRawIntBits(vel), offset, len, isMuted, isSelected]
        long[] packedInput = new long[inputNotes.size() * 6];
        for (int i = 0; i < inputNotes.size(); i++) {
            ClipItem.Note n = inputNotes.get(i);
            packedInput[i * 6] = n.note;
            packedInput[i * 6 + 1] = Float.floatToRawIntBits(n.velocity);
            packedInput[i * 6 + 2] = n.startOffsetTicks;
            packedInput[i * 6 + 3] = n.lengthTicks;
            packedInput[i * 6 + 4] = n.isMuted ? 1 : 0;
            packedInput[i * 6 + 5] = n.isSelected ? 1 : 0;
        }

        // Bitmask for scale
        int scaleMask = 0b111111111111;
        if (scale == MusicalScale.MAJOR) scaleMask = 0b101010110101;
        else if (scale == MusicalScale.NATURAL_MINOR) scaleMask = 0b010110101101;
        else if (scale == MusicalScale.DORIAN) scaleMask = 0b010101101101;
        else if (scale == MusicalScale.MINOR_PENTATONIC) scaleMask = 0b010010101001;
        else if (scale == MusicalScale.MAJOR_PENTATONIC) scaleMask = 0b001010100101;

        long[] currentPacked = packedInput;

        // Sequentially execute each recipe in the stack
        for (TransformRecipeItem recipe : recipeStack) {
            if (!recipe.enabled) continue;

            long[] result = AudioEngineNative.nativeExecuteTransformPipeline(
                currentPacked,
                rootKey,
                scaleMask,
                480, // PPQ
                4,   // 4/4 meter
                recipe.type.ordinal,
                recipe.intensity,
                recipe.seed,
                recipe.param1,
                recipe.param2,
                masks.lockDownbeats,
                masks.lockPitches,
                masks.lockRhythm,
                masks.lockVelocities,
                masks.lockBassNotes,
                dryWetRatio
            );

            if (result != null && result.length >= 6) {
                currentPacked = result;
            }
        }

        // Unpack to List<ClipItem.Note>
        List<ClipItem.Note> outNotes = new ArrayList<>();
        int count = currentPacked.length / 6;
        for (int i = 0; i < count; i++) {
            int pitch = (int) currentPacked[i * 6];
            float vel = Float.intBitsToFloat((int) currentPacked[i * 6 + 1]);
            long offset = currentPacked[i * 6 + 2];
            long len = currentPacked[i * 6 + 3];
            boolean muted = currentPacked[i * 6 + 4] != 0;
            boolean sel = currentPacked[i * 6 + 5] != 0;

            ClipItem.Note n = new ClipItem.Note(pitch, vel, offset, len, muted);
            n.isSelected = sel;
            outNotes.add(n);
        }

        return outNotes;
    }
}
