# Text editor

A text editor written from scratch in Java with Swing. The text is
painted by hand onto a `JPanel`; the document, the undo history, and
the display are all built here rather than taken from Swing's text
package.

## Building and running

    javac -d bin src/editor/*.java
    java -cp bin editor.Main <file>

The file is the one and only argument. A file that does not exist yet
starts out empty, so the editor can be used to create it.

## Keys

| Key | Action |
| --- | --- |
| Printable characters | Insert at the cursor, replacing any selection |
| Enter | Insert a newline |
| Backspace | Delete the character before the cursor, or the selection |
| Delete | Delete the character at the cursor, or the selection |
| Arrow keys | Move the cursor; up and down keep their column |
| Control-S | Save back to the file |
| Control-O | Open another window on the same file |
| Control-C | Copy the selection to the system clipboard |
| Control-V | Paste the clipboard at the cursor |
| Control-Z | Undo |
| Control-Y | Redo |

Command works in place of Control as well, since that is what the rest
of the system uses on a Mac.

Click to place the cursor, drag to select, and shift-click to extend an
existing selection as though the button had never been released.

## How it fits together

    Main            reads the file and opens the first window
    TextDocument    the text, as a list of newline-free lines
    Position        an immutable (line, column) location
    Edit            one undoable change, with the cursor either side
    EditHistory     the shared undo and redo stacks
    EditorPanel     paints one view and turns input into edits
    EditorWindow    a frame wrapping one panel in scrollbars

`TextDocument` holds the file as a list of lines with the newlines
implied between them, which keeps line lookup during painting a plain
array index. Every change goes through `insert` or `delete`, and both
report what they did to each registered view, so several windows on one
file stay in step: a window that did not make the change maps its own
cursor and selection through the change and repaints.

`EditHistory` is shared by all the windows on a document, so there is a
single undo history across all of them. Each `Edit` records the text
removed, the text added, and the cursor and selection on either side of
the change, which is what undo and redo put back. A run of typing, or a
run of backspaces, collapses into one `Edit` so that a single undo
reverses the whole run; moving the cursor, or typing in a different
window, ends the run.

Painting touches only what is on screen. `paintComponent` turns the
clip rectangle into a range of lines and a range of columns and draws
just that, so a ten megabyte file costs no more to paint than a short
one. The panel implements `Scrollable` and reports a preferred size
derived from the line count and the longest line, which is what gives
the scrollbar thumbs their size; the longest line is cached and only
recomputed when a deletion could have shortened it.
