package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.ClipItem;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class PianoRollHistoryManager {
    private static final int MAX_UNDO_STACK = 50;
    private final Deque<List<ClipItem.Note>> undoStack = new ArrayDeque<>();
    private final Deque<List<ClipItem.Note>> redoStack = new ArrayDeque<>();

    public void captureUndoPoint(ClipItem clip) {
        if (clip != null) {
            pushUndoState(clip.cloneNotesList());
        }
    }

    public void pushUndoState(List<ClipItem.Note> snapshot) {
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

    public boolean undo(ClipItem clip) {
        if (undoStack.isEmpty() || clip == null) return false;
        redoStack.push(clip.cloneNotesList());
        List<ClipItem.Note> previous = undoStack.pop();
        clip.restoreNotesList(previous);
        return true;
    }

    public boolean redo(ClipItem clip) {
        if (redoStack.isEmpty() || clip == null) return false;
        undoStack.push(clip.cloneNotesList());
        List<ClipItem.Note> next = redoStack.pop();
        clip.restoreNotesList(next);
        return true;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
