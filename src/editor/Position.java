package editor;

/**
 * An immutable location in a document, given as a zero-based line
 * index and a zero-based column index. Column c refers to the gap just
 * before the character at index c on that line; a column equal to the
 * line's length refers to the gap just before the newline that
 * terminates the line.
 */
public final class Position implements Comparable<Position> {
    public final int line;
    public final int column;

    public Position(int line, int column) {
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the position just past the end of text, assuming that
     * text was inserted starting at start.
     */
    public static Position advance(Position start, String text) {
        int last = text.lastIndexOf('\n');
        if (last < 0) {
            return new Position(start.line,
                    start.column + text.length());
        }
        int newlines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                newlines++;
            }
        }
        return new Position(start.line + newlines,
                text.length() - last - 1);
    }

    /**
     * Maps a position recorded before a document change onto the
     * equivalent position after the change. The change replaced the
     * text between start and oldEnd with text that now ends at newEnd.
     * Positions inside the replaced range collapse to start.
     */
    public static Position adjust(Position p, Position start,
            Position oldEnd, Position newEnd) {
        if (p.compareTo(start) <= 0) {
            return p;
        }
        if (p.compareTo(oldEnd) < 0) {
            return start;
        }
        if (p.line == oldEnd.line) {
            return new Position(newEnd.line,
                    newEnd.column + (p.column - oldEnd.column));
        }
        return new Position(p.line + (newEnd.line - oldEnd.line),
                p.column);
    }

    @Override
    public int compareTo(Position other) {
        if (line != other.line) {
            return line < other.line ? -1 : 1;
        }
        if (column != other.column) {
            return column < other.column ? -1 : 1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Position)) {
            return false;
        }
        Position other = (Position) o;
        return line == other.line && column == other.column;
    }

    @Override
    public int hashCode() {
        return line * 31 + column;
    }

    @Override
    public String toString() {
        return "(" + line + "," + column + ")";
    }
}
