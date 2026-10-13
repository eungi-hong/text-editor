package editor;

/**
 * Notified whenever the text of a TextDocument changes. Every view of
 * the document registers one of these so that all windows stay in
 * step.
 */
public interface DocumentListener {
    /**
     * Invoked after the document has replaced the text that used to
     * run from start to oldEnd with text that now runs from start to
     * newEnd. Either range may be empty (a pure insertion has
     * start.equals(oldEnd); a pure deletion has start.equals(newEnd)).
     */
    void documentChanged(Position start, Position oldEnd,
            Position newEnd);
}
