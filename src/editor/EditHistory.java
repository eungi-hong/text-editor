package editor;

import java.util.ArrayList;

/**
 * The undo and redo stacks for one document. A single history is
 * shared by every window on the document, so an undo in one window can
 * reverse a change made in another.
 *
 * Consecutive keystrokes of the same kind, made in the same window
 * without the cursor being moved in between, are merged into one edit
 * so that a single undo reverses the whole run.
 */
public class EditHistory {
    /** The kind of run an edit can belong to, if any. */
    public enum Group {
        /** Not part of any run: always its own undo step. */
        NONE,
        TYPING,
        BACKSPACE,
        DELETE
    }

    private final TextDocument document;
    private final ArrayList<Edit> undoStack = new ArrayList<Edit>();
    private final ArrayList<Edit> redoStack = new ArrayList<Edit>();

    /** The run the top of the undo stack currently belongs to. */
    private Group openGroup = Group.NONE;

    /** The window that opened that run; runs never span windows. */
    private Object openSource;

    public EditHistory(TextDocument document) {
        this.document = document;
    }

    /**
     * Records a change that has already been applied to the document,
     * merging it into the run on top of the stack when it continues
     * one. Recording anything discards the redo stack.
     */
    public void record(Edit edit, Group kind, Object source) {
        redoStack.clear();
        if (kind != Group.NONE && kind == openGroup
                && source == openSource && !undoStack.isEmpty()) {
            int top = undoStack.size() - 1;
            Edit merged = merge(undoStack.get(top), edit, kind);
            if (merged != null) {
                undoStack.set(top, merged);
                return;
            }
        }
        undoStack.add(edit);
        openGroup = kind;
        openSource = source;
    }

    /**
     * Ends the current run, so the next keystroke starts a fresh undo
     * step. Called whenever the cursor is repositioned.
     */
    public void breakGroup() {
        openGroup = Group.NONE;
        openSource = null;
    }

    /**
     * Reverses the most recent edit and returns it, or null if there
     * is nothing left to undo. The caller restores edit.before.
     */
    public Edit undo() {
        breakGroup();
        if (undoStack.isEmpty()) {
            return null;
        }
        Edit edit = undoStack.remove(undoStack.size() - 1);
        apply(edit, true);
        redoStack.add(edit);
        return edit;
    }

    /**
     * Reapplies the most recently undone edit and returns it, or null
     * if there is nothing to redo. The caller restores edit.after.
     */
    public Edit redo() {
        breakGroup();
        if (redoStack.isEmpty()) {
            return null;
        }
        Edit edit = redoStack.remove(redoStack.size() - 1);
        apply(edit, false);
        undoStack.add(edit);
        return edit;
    }

    private void apply(Edit edit, boolean undoing) {
        String remove = undoing ? edit.inserted : edit.deleted;
        String add = undoing ? edit.deleted : edit.inserted;
        if (!remove.isEmpty()) {
            document.delete(edit.start,
                    Position.advance(edit.start, remove));
        }
        if (!add.isEmpty()) {
            document.insert(edit.start, add);
        }
    }

    /**
     * Combines two edits of the same kind into one, or returns null if
     * they are not actually adjacent and so cannot be combined. The
     * result keeps the earlier edit's "before" state and the later
     * edit's "after" state.
     */
    private static Edit merge(Edit prev, Edit next, Group kind) {
        switch (kind) {
        case TYPING:
            // The new text has to start exactly where the run's text
            // ended, and a newline always begins a fresh step.
            if (!next.deleted.isEmpty()
                    || next.inserted.indexOf('\n') >= 0) {
                return null;
            }
            if (!Position.advance(prev.start, prev.inserted)
                    .equals(next.start)) {
                return null;
            }
            return new Edit(prev.start, prev.deleted,
                    prev.inserted + next.inserted, prev.before,
                    next.after);
        case BACKSPACE:
            // Backspace walks leftwards, so each deletion ends where
            // the run so far begins.
            if (!prev.inserted.isEmpty() || !next.inserted.isEmpty()) {
                return null;
            }
            if (!Position.advance(next.start, next.deleted)
                    .equals(prev.start)) {
                return null;
            }
            return new Edit(next.start, next.deleted + prev.deleted,
                    "", prev.before, next.after);
        case DELETE:
            // Forward delete keeps eating at the same spot.
            if (!prev.inserted.isEmpty() || !next.inserted.isEmpty()) {
                return null;
            }
            if (!next.start.equals(prev.start)) {
                return null;
            }
            return new Edit(prev.start, prev.deleted + next.deleted,
                    "", prev.before, next.after);
        default:
            return null;
        }
    }
}
