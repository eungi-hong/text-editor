package editor;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

/**
 * A top-level window onto a document. Any number of these may be open
 * on the same document; they share its text and its undo history but
 * scroll independently and each keep a cursor of their own.
 */
public class EditorWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;

    /** Open windows; the program exits when the last one closes. */
    private static int openWindows = 0;

    public EditorWindow(TextDocument document, EditHistory history) {
        super(document.getName());
        openWindows++;

        final EditorPanel panel = new EditorPanel(document, history);
        JScrollPane scrollPane = new JScrollPane(panel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        setContentPane(scrollPane);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                openWindows--;
                if (openWindows == 0) {
                    System.exit(0);
                }
            }
        });

        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setLocationByPlatform(true);
        setVisible(true);
        panel.requestFocusInWindow();
    }
}
