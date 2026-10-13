package editor;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * One view of a document: it paints the text, owns a cursor and a
 * selection of its own, and turns keystrokes and mouse gestures into
 * edits. Several panels may share one document and one history.
 *
 * Only the lines that intersect the clip rectangle are painted, and
 * only the columns that are actually on screen, so painting costs the
 * same whether the file has ten lines or a million.
 */
public class EditorPanel extends JPanel
        implements Scrollable, DocumentListener {

    private static final long serialVersionUID = 1L;

    /** Left margin, so the cursor at column 0 is not clipped away. */
    private static final int LEFT_PAD = 2;

    /** Half a blink: on for this long, then off for this long. */
    private static final int BLINK_MS = 500;

    private static final Color SELECTION_COLOR =
            new Color(181, 213, 255);

    private static final int CARET_WIDTH = 2;

    private final TextDocument document;
    private final EditHistory history;

    private Position caret = new Position(0, 0);

    /** Fixed end of the selection, or null when nothing is selected. */
    private Position anchor;

    /**
     * The column the up and down arrows aim for, so that passing
     * through a short line does not drag the cursor leftwards
     * permanently. -1 means "use the cursor's current column".
     */
    private int goalColumn = -1;

    private boolean dragging;
    private boolean focused;
    private boolean caretOn = true;
    private final Timer blinkTimer;

    private final int charWidth;
    private final int lineHeight;
    private final int ascent;

    public EditorPanel(TextDocument document, EditHistory history) {
        this.document = document;
        this.history = history;

        setFont(new Font("Monospaced", Font.PLAIN, 12));
        FontMetrics metrics = getFontMetrics(getFont());
        charWidth = metrics.charWidth('m');
        lineHeight = metrics.getHeight();
        ascent = metrics.getAscent();

        setBackground(Color.WHITE);
        setOpaque(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        document.addListener(this);

        blinkTimer = new Timer(BLINK_MS, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                caretOn = !caretOn;
                repaint(caretRect());
            }
        });
        blinkTimer.start();

        MouseAdapter mouse = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                handlePress(e);
            }

            public void mouseDragged(MouseEvent e) {
                handleDrag(e);
            }

            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                handleKeyPressed(e);
            }

            public void keyTyped(KeyEvent e) {
                handleKeyTyped(e);
            }
        });

        addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) {
                focused = true;
                showCaret();
            }

            public void focusLost(FocusEvent e) {
                focused = false;
                dragging = false;
                history.breakGroup();
                repaint(caretRect());
            }
        });
    }

    // ---------------------------------------------------------------
    // Painting
    // ---------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }

        int firstLine = Math.max(0, clip.y / lineHeight);
        int lastLine = Math.min(document.getLineCount() - 1,
                (clip.y + clip.height - 1) / lineHeight);
        int firstColumn = Math.max(0,
                (clip.x - LEFT_PAD) / charWidth);
        int lastColumn =
                (clip.x + clip.width - LEFT_PAD) / charWidth + 1;

        Position selectionStart = getSelectionStart();
        Position selectionEnd = getSelectionEnd();

        g.setFont(getFont());
        for (int i = firstLine; i <= lastLine; i++) {
            String line = document.getLine(i);
            int y = i * lineHeight;
            if (selectionStart != null) {
                paintSelection(g, i, line.length(), selectionStart,
                        selectionEnd, y);
            }
            int from = Math.min(firstColumn, line.length());
            int to = Math.min(lastColumn, line.length());
            if (to > from) {
                g.setColor(Color.BLACK);
                g.drawString(visible(line.substring(from, to)),
                        LEFT_PAD + from * charWidth, y + ascent);
            }
        }

        if (focused && caretOn) {
            g.setColor(Color.BLACK);
            Rectangle r = caretRect();
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }

    /** Highlights the part of one line that falls in the selection. */
    private void paintSelection(Graphics g, int lineIndex,
            int lineLength, Position start, Position end, int y) {
        if (lineIndex < start.line || lineIndex > end.line) {
            return;
        }
        int from = (lineIndex == start.line) ? start.column : 0;
        // A line wholly inside the selection has its newline selected
        // too, shown as one extra character of highlight.
        int to = (lineIndex == end.line) ? end.column : lineLength + 1;
        if (to <= from) {
            return;
        }
        g.setColor(SELECTION_COLOR);
        g.fillRect(LEFT_PAD + from * charWidth, y,
                (to - from) * charWidth, lineHeight);
    }

    /**
     * Tabs are not supported as adjustable indents, but a file may
     * still contain them; drawing each as one space keeps every
     * character exactly one column wide, so the text stays lined up
     * with the cursor and the selection.
     */
    private static String visible(String text) {
        if (text.indexOf('\t') < 0) {
            return text;
        }
        return text.replace('\t', ' ');
    }

    private Rectangle caretRect() {
        return new Rectangle(
                LEFT_PAD + caret.column * charWidth - CARET_WIDTH / 2,
                caret.line * lineHeight, CARET_WIDTH, lineHeight);
    }

    /** Restarts the blink cycle with the cursor on. */
    private void showCaret() {
        caretOn = true;
        blinkTimer.restart();
        repaint();
    }

    // ---------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------

    @Override
    public Dimension getPreferredSize() {
        int width = 2 * LEFT_PAD
                + (document.getMaxLineLength() + 1) * charWidth;
        int height = document.getLineCount() * lineHeight;
        return new Dimension(width, height);
    }

    /** Finds the insertion point closest to a point in the panel. */
    private Position positionForPoint(Point point) {
        int line = point.y / lineHeight;
        if (point.y < 0) {
            line = 0;
        }
        if (line < 0) {
            line = 0;
        } else if (line >= document.getLineCount()) {
            line = document.getLineCount() - 1;
        }
        int column = (int) Math.round(
                (point.x - LEFT_PAD) / (double) charWidth);
        int length = document.getLineLength(line);
        if (column < 0) {
            column = 0;
        } else if (column > length) {
            column = length;
        }
        return new Position(line, column);
    }

    /**
     * Scrolls so that the cursor is on screen. The scroll is deferred
     * until after the layout that follows a change, so that it is
     * measured against the document's new size.
     */
    private void scrollCaretToVisible() {
        revalidate();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Rectangle r = caretRect();
                // A little margin either side keeps the cursor off the
                // very edge of the window.
                r.x -= charWidth;
                r.width += 2 * charWidth;
                scrollRectToVisible(r);
            }
        });
    }

    // ---------------------------------------------------------------
    // Selection
    // ---------------------------------------------------------------

    private boolean hasSelection() {
        return anchor != null && !anchor.equals(caret);
    }

    private Position getSelectionStart() {
        if (!hasSelection()) {
            return null;
        }
        return anchor.compareTo(caret) < 0 ? anchor : caret;
    }

    private Position getSelectionEnd() {
        if (!hasSelection()) {
            return null;
        }
        return anchor.compareTo(caret) < 0 ? caret : anchor;
    }

    private ViewState viewState() {
        return new ViewState(caret, anchor);
    }

    private void restore(ViewState state) {
        caret = document.clamp(state.caret);
        anchor = state.anchor == null ? null
                : document.clamp(state.anchor);
        goalColumn = -1;
    }

    // ---------------------------------------------------------------
    // Mouse
    // ---------------------------------------------------------------

    private void handlePress(MouseEvent e) {
        requestFocusInWindow();
        history.breakGroup();
        Position clicked = positionForPoint(e.getPoint());
        if (e.isShiftDown() && anchor != null) {
            // Carry on from the existing selection, as if the button
            // had never been released.
            caret = clicked;
        } else {
            // An empty selection: a click with no drag cancels
            // whatever was selected before.
            anchor = clicked;
            caret = clicked;
        }
        dragging = true;
        goalColumn = -1;
        showCaret();
    }

    private void handleDrag(MouseEvent e) {
        if (!dragging) {
            return;
        }
        caret = positionForPoint(e.getPoint());
        goalColumn = -1;
        scrollCaretToVisible();
        showCaret();
    }

    // ---------------------------------------------------------------
    // Keyboard
    // ---------------------------------------------------------------

    private void handleKeyPressed(KeyEvent e) {
        // Command is accepted alongside Control so the usual shortcuts
        // also work the way they do elsewhere on a Mac.
        if (e.isControlDown() || e.isMetaDown()) {
            switch (e.getKeyCode()) {
            case KeyEvent.VK_S:
                save();
                e.consume();
                break;
            case KeyEvent.VK_O:
                new EditorWindow(document, history);
                e.consume();
                break;
            case KeyEvent.VK_C:
                copy();
                e.consume();
                break;
            case KeyEvent.VK_V:
                paste();
                e.consume();
                break;
            case KeyEvent.VK_Z:
                undo();
                e.consume();
                break;
            case KeyEvent.VK_Y:
                redo();
                e.consume();
                break;
            default:
                break;
            }
            return;
        }

        switch (e.getKeyCode()) {
        case KeyEvent.VK_LEFT:
            moveHorizontally(-1);
            e.consume();
            break;
        case KeyEvent.VK_RIGHT:
            moveHorizontally(1);
            e.consume();
            break;
        case KeyEvent.VK_UP:
            moveVertically(-1);
            e.consume();
            break;
        case KeyEvent.VK_DOWN:
            moveVertically(1);
            e.consume();
            break;
        case KeyEvent.VK_BACK_SPACE:
            deleteBackward();
            e.consume();
            break;
        case KeyEvent.VK_DELETE:
            deleteForward();
            e.consume();
            break;
        case KeyEvent.VK_ENTER:
            replaceSelection("\n", EditHistory.Group.NONE);
            e.consume();
            break;
        default:
            break;
        }
    }

    private void handleKeyTyped(KeyEvent e) {
        if (e.isControlDown() || e.isMetaDown() || e.isAltDown()) {
            return;
        }
        char c = e.getKeyChar();
        // Enter, Backspace and Delete are handled as key presses; what
        // is left here is the printable characters.
        if (c == KeyEvent.CHAR_UNDEFINED || c < ' ' || c == '\u007f') {
            return;
        }
        replaceSelection(String.valueOf(c),
                EditHistory.Group.TYPING);
        e.consume();
    }

    // ---------------------------------------------------------------
    // Cursor movement
    // ---------------------------------------------------------------

    private void moveHorizontally(int direction) {
        Position target;
        if (direction < 0) {
            if (caret.column > 0) {
                target = new Position(caret.line, caret.column - 1);
            } else if (caret.line > 0) {
                int above = caret.line - 1;
                target = new Position(above,
                        document.getLineLength(above));
            } else {
                target = caret;
            }
        } else {
            if (caret.column < document.getLineLength(caret.line)) {
                target = new Position(caret.line, caret.column + 1);
            } else if (caret.line < document.getLineCount() - 1) {
                target = new Position(caret.line + 1, 0);
            } else {
                target = caret;
            }
        }
        moveCaretTo(target);
        goalColumn = -1;
    }

    private void moveVertically(int direction) {
        if (goalColumn < 0) {
            goalColumn = caret.column;
        }
        int line = caret.line + direction;
        if (line < 0 || line >= document.getLineCount()) {
            return;
        }
        int column = Math.min(goalColumn,
                document.getLineLength(line));
        int remembered = goalColumn;
        moveCaretTo(new Position(line, column));
        goalColumn = remembered;
    }

    private void moveCaretTo(Position target) {
        history.breakGroup();
        caret = target;
        anchor = null;
        goalColumn = -1;
        scrollCaretToVisible();
        showCaret();
    }

    // ---------------------------------------------------------------
    // Editing
    // ---------------------------------------------------------------

    /**
     * Replaces the selection, or inserts at the cursor when there is
     * no selection.
     */
    private void replaceSelection(String text,
            EditHistory.Group kind) {
        Position start = hasSelection() ? getSelectionStart() : caret;
        Position end = hasSelection() ? getSelectionEnd() : caret;
        performEdit(start, end, text, kind);
    }

    private void deleteBackward() {
        if (hasSelection()) {
            performEdit(getSelectionStart(), getSelectionEnd(), "",
                    EditHistory.Group.NONE);
            return;
        }
        Position from;
        if (caret.column > 0) {
            from = new Position(caret.line, caret.column - 1);
        } else if (caret.line > 0) {
            int above = caret.line - 1;
            from = new Position(above, document.getLineLength(above));
        } else {
            return;
        }
        performEdit(from, caret, "", EditHistory.Group.BACKSPACE);
    }

    private void deleteForward() {
        if (hasSelection()) {
            performEdit(getSelectionStart(), getSelectionEnd(), "",
                    EditHistory.Group.NONE);
            return;
        }
        Position to;
        if (caret.column < document.getLineLength(caret.line)) {
            to = new Position(caret.line, caret.column + 1);
        } else if (caret.line < document.getLineCount() - 1) {
            to = new Position(caret.line + 1, 0);
        } else {
            return;
        }
        performEdit(caret, to, "", EditHistory.Group.DELETE);
    }

    /**
     * Applies one change to the document and records it, leaving the
     * cursor just past whatever was inserted.
     */
    private void performEdit(Position start, Position end,
            String inserted, EditHistory.Group kind) {
        if (start.equals(end) && inserted.isEmpty()) {
            return;
        }
        ViewState before = viewState();
        String deleted = document.delete(start, end);
        Position finish = document.insert(start, inserted);

        caret = finish;
        anchor = null;
        goalColumn = -1;
        history.record(
                new Edit(start, deleted, inserted, before, viewState()),
                kind, this);
        scrollCaretToVisible();
        showCaret();
    }

    private void undo() {
        Edit edit = history.undo();
        if (edit == null) {
            return;
        }
        restore(edit.before);
        scrollCaretToVisible();
        showCaret();
    }

    private void redo() {
        Edit edit = history.redo();
        if (edit == null) {
            return;
        }
        restore(edit.after);
        scrollCaretToVisible();
        showCaret();
    }

    // ---------------------------------------------------------------
    // Clipboard and file
    // ---------------------------------------------------------------

    private void copy() {
        if (!hasSelection()) {
            return;
        }
        String text = document.getText(getSelectionStart(),
                getSelectionEnd());
        Clipboard clipboard =
                Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    private void paste() {
        Clipboard clipboard =
                Toolkit.getDefaultToolkit().getSystemClipboard();
        String text;
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(
                    DataFlavor.stringFlavor)) {
                return;
            }
            text = (String) contents.getTransferData(
                    DataFlavor.stringFlavor);
        } catch (Exception e) {
            return;
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        if (text.indexOf('\r') >= 0) {
            text = text.replace("\r\n", "\n").replace('\r', '\n');
        }
        replaceSelection(text, EditHistory.Group.NONE);
    }

    private void save() {
        try {
            document.save();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't save the file: " + e.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------
    // Reacting to changes made anywhere
    // ---------------------------------------------------------------

    /**
     * Keeps this window's cursor and selection pointing at the same
     * text after any window edits the document. The window that made
     * the change overwrites these values immediately afterwards.
     */
    public void documentChanged(Position start, Position oldEnd,
            Position newEnd) {
        caret = Position.adjust(caret, start, oldEnd, newEnd);
        if (anchor != null) {
            anchor = Position.adjust(anchor, start, oldEnd, newEnd);
        }
        goalColumn = -1;
        revalidate();
        repaint();
    }

    // ---------------------------------------------------------------
    // Scrollable
    // ---------------------------------------------------------------

    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(80 * charWidth, 30 * lineHeight);
    }

    public int getScrollableUnitIncrement(Rectangle visibleRect,
            int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
                ? lineHeight : charWidth;
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect,
            int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
                ? Math.max(lineHeight, visibleRect.height - lineHeight)
                : Math.max(charWidth, visibleRect.width - charWidth);
    }

    public boolean getScrollableTracksViewportWidth() {
        Container parent = getParent();
        return parent instanceof JViewport
                && parent.getWidth() > getPreferredSize().width;
    }

    public boolean getScrollableTracksViewportHeight() {
        Container parent = getParent();
        return parent instanceof JViewport
                && parent.getHeight() > getPreferredSize().height;
    }
}
