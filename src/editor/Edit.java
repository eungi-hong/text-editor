package editor;

/**
 * One undoable change: the text between start and the end of deleted
 * was replaced by inserted. Either string may be empty, so this covers
 * pure insertions, pure deletions, and replacements alike.
 *
 * Edits are immutable. Grouping consecutive keystrokes works by
 * replacing the edit on top of the undo stack with a wider one.
 */
public final class Edit {
    public final Position start;
    public final String deleted;
    public final String inserted;

    /** Cursor and selection just before the change was first made. */
    public final ViewState before;

    /** Cursor and selection just after the change was first made. */
    public final ViewState after;

    public Edit(Position start, String deleted, String inserted,
            ViewState before, ViewState after) {
        this.start = start;
        this.deleted = deleted;
        this.inserted = inserted;
        this.before = before;
        this.after = after;
    }
}
