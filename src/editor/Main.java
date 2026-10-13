package editor;

import java.io.IOException;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;

/** Starts the editor on the file named on the command line. */
public class Main {
    public static void main(final String[] args) {
        if (args.length != 1) {
            System.err.println("usage: java editor.Main <file>");
            System.exit(1);
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    TextDocument document =
                            new TextDocument(Paths.get(args[0]));
                    new EditorWindow(document,
                            new EditHistory(document));
                } catch (IOException e) {
                    System.err.println("couldn't read " + args[0]
                            + ": " + e.getMessage());
                    System.exit(1);
                }
            }
        });
    }
}
