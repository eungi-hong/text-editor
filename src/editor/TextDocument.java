package editor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The text of one file, held as a list of lines that never contain
 * newline characters. The list always holds at least one line, so the
 * position (0,0) is always valid. Newlines are implicit between
 * consecutive lines, which means a document of n lines holds n-1
 * newline characters.
 *
 * All editing goes through insert and delete; both report what they
 * did to every registered listener, which is how several windows on
 * the same file stay consistent.
 */
public class TextDocument {
    private final ArrayList<String> lines = new ArrayList<String>();
    private final List<DocumentListener> listeners =
            new ArrayList<DocumentListener>();
    private final Path path;

    /**
     * Length of the longest line, used to size the horizontal
     * scrollbar. Recomputing this is linear in the document, so the
     * value is cached and only invalidated when a deletion might have
     * removed the longest line.
     */
    private int maxLineLength;
    private boolean maxLineLengthValid;

    /**
     * Reads the given file. A file that does not exist yet yields an
     * empty document, so the editor can be used to create it.
     */
    public TextDocument(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            byte[] bytes = Files.readAllBytes(path);
            setContents(new String(bytes, StandardCharsets.UTF_8));
        } else {
            lines.add("");
        }
        maxLineLengthValid = false;
    }

    /** Splits raw file text into lines, normalizing "\r\n" to "\n". */
    private void setContents(String text) {
        if (text.indexOf('\r') >= 0) {
            text = text.replace("\r\n", "\n");
        }
        lines.clear();
        int start = 0;
        while (true) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) {
                lines.add(text.substring(start));
                return;
            }
            lines.add(text.substring(start, newline));
            start = newline + 1;
        }
    }

    public void addListener(DocumentListener listener) {
        listeners.add(listener);
    }

    public String getName() {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    public int getLineCount() {
        return lines.size();
    }

    public String getLine(int index) {
        return lines.get(index);
    }

    public int getLineLength(int index) {
        return lines.get(index).length();
    }

    public int getMaxLineLength() {
        if (!maxLineLengthValid) {
            int max = 0;
            for (int i = 0; i < lines.size(); i++) {
                int length = lines.get(i).length();
                if (length > max) {
                    max = length;
                }
            }
            maxLineLength = max;
            maxLineLengthValid = true;
        }
        return maxLineLength;
    }

    /** Returns the last valid position in the document. */
    public Position getEnd() {
        int last = lines.size() - 1;
        return new Position(last, lines.get(last).length());
    }

    /** Forces a position onto the nearest location that exists. */
    public Position clamp(Position p) {
        int line = p.line;
        if (line < 0) {
            line = 0;
        } else if (line >= lines.size()) {
            line = lines.size() - 1;
        }
        int column = p.column;
        int length = lines.get(line).length();
        if (column < 0) {
            column = 0;
        } else if (column > length) {
            column = length;
        }
        if (line == p.line && column == p.column) {
            return p;
        }
        return new Position(line, column);
    }

    /** Returns the text between two positions, start inclusive. */
    public String getText(Position start, Position end) {
        if (start.equals(end)) {
            return "";
        }
        if (start.line == end.line) {
            return lines.get(start.line).substring(start.column,
                    end.column);
        }
        StringBuilder result = new StringBuilder();
        result.append(lines.get(start.line).substring(start.column));
        for (int i = start.line + 1; i < end.line; i++) {
            result.append('\n').append(lines.get(i));
        }
        result.append('\n');
        result.append(lines.get(end.line).substring(0, end.column));
        return result.toString();
    }

    /**
     * Inserts text at the given position and returns the position just
     * past the inserted text.
     */
    public Position insert(Position at, String text) {
        if (text.isEmpty()) {
            return at;
        }
        String line = lines.get(at.line);
        String head = line.substring(0, at.column);
        String tail = line.substring(at.column);

        int firstBreak = text.indexOf('\n');
        if (firstBreak < 0) {
            setLine(at.line, head + text + tail);
            Position end = new Position(at.line,
                    at.column + text.length());
            fireChange(at, at, end);
            return end;
        }

        // The insertion spans lines: the original line is cut in two
        // and the interior lines of the new text go in between.
        setLine(at.line, head + text.substring(0, firstBreak));
        List<String> inserted = new ArrayList<String>();
        int start = firstBreak + 1;
        while (true) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) {
                inserted.add(text.substring(start) + tail);
                break;
            }
            inserted.add(text.substring(start, newline));
            start = newline + 1;
        }
        lines.addAll(at.line + 1, inserted);
        for (int i = 0; i < inserted.size(); i++) {
            noteLineLength(inserted.get(i).length());
        }
        Position end = new Position(at.line + inserted.size(),
                text.length() - text.lastIndexOf('\n') - 1);
        fireChange(at, at, end);
        return end;
    }

    /**
     * Deletes the text between two positions and returns what was
     * removed.
     */
    public String delete(Position start, Position end) {
        if (start.equals(end)) {
            return "";
        }
        String deleted = getText(start, end);

        // If the longest line is among the ones being changed, the
        // cached maximum can no longer be trusted.
        if (maxLineLengthValid) {
            for (int i = start.line; i <= end.line; i++) {
                if (lines.get(i).length() >= maxLineLength) {
                    maxLineLengthValid = false;
                    break;
                }
            }
        }

        String head = lines.get(start.line).substring(0, start.column);
        String tail = lines.get(end.line).substring(end.column);
        if (end.line > start.line) {
            lines.subList(start.line + 1, end.line + 1).clear();
        }
        // Joining two lines can produce a line longer than either of
        // them, so the maximum has to be offered the new length.
        setLine(start.line, head + tail);
        fireChange(start, end, start);
        return deleted;
    }

    /** Writes the document back to its file, using "\n" throughout. */
    public void save() throws IOException {
        BufferedWriter out = Files.newBufferedWriter(path,
                StandardCharsets.UTF_8);
        try {
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    out.write('\n');
                }
                out.write(lines.get(i));
            }
        } finally {
            out.close();
        }
    }

    private void setLine(int index, String text) {
        lines.set(index, text);
        noteLineLength(text.length());
    }

    private void noteLineLength(int length) {
        if (maxLineLengthValid && length > maxLineLength) {
            maxLineLength = length;
        }
    }

    private void fireChange(Position start, Position oldEnd,
            Position newEnd) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).documentChanged(start, oldEnd, newEnd);
        }
    }
}
