package editor;

/**
 * The cursor and selection of one window at one instant. Undo records
 * carry a pair of these so that undoing or redoing can put the cursor
 * and selection back where the user left them.
 */
public final class ViewState {
    public final Position caret;

    /**
     * The fixed end of the selection, or null if nothing is selected.
     * The moving end of the selection is always the caret.
     */
    public final Position anchor;

    public ViewState(Position caret, Position anchor) {
        this.caret = caret;
        this.anchor = anchor;
    }
}
