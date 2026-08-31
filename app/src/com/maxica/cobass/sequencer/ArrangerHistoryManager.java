package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.ClipItem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ArrangerHistoryManager {
    private static final int MAX_UNDO_STACK = 50;
    private final Deque<List<ClipItem>> undoStack = new ArrayDeque<>();
    private final Deque<List<ClipItem>> redoStack = new ArrayDeque<>();

    public void captureUndoPoint(List<ClipItem> clips) {
        if (clips == null) return;
        pushUndoState(cloneClipsList(clips));
    }

    public void pushUndoState(List<ClipItem> snapshot) {
        if (snapshot == null) return;
        if (undoStack.size() >= MAX_UNDO_STACK) {
            undoStack.removeLast();
        }
        undoStack.push(snapshot);
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undo(List<ClipItem> targetList) {
        if (undoStack.isEmpty() || targetList == null) return false;
        redoStack.push(cloneClipsList(targetList));
        List<ClipItem> previous = undoStack.pop();
        restoreClipsList(targetList, previous);
        return true;
    }

    public boolean redo(List<ClipItem> targetList) {
        if (redoStack.isEmpty() || targetList == null) return false;
        undoStack.push(cloneClipsList(targetList));
        List<ClipItem> next = redoStack.pop();
        restoreClipsList(targetList, next);
        return true;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public static List<ClipItem> cloneClipsList(List<ClipItem> source) {
        List<ClipItem> copy = new ArrayList<>(source.size());
        for (ClipItem c : source) {
            copy.add(c.copy());
        }
        return copy;
    }

    public static void restoreClipsList(List<ClipItem> targetList, List<ClipItem> snapshot) {
        targetList.clear();
        if (snapshot != null) {
            for (ClipItem c : snapshot) {
                targetList.add(c.copy());
            }
        }
    }
}
