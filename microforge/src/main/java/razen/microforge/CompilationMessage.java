package razen.microforge;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class CompilationMessage implements AutoCloseable {
    private static final String CANCEL = "Cancel";

    private final JDialog dialog;
    private final AtomicBoolean closed;

    private CompilationMessage(JDialog dialog, AtomicBoolean closed) {
        this.dialog = dialog;
        this.closed = closed;
    }

    static CompilationMessage show() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return new CompilationMessage(null, new AtomicBoolean());
        }
        var result = new AtomicReference<CompilationMessage>();
        SwingUtilities.invokeAndWait(() -> {
            var closed = new AtomicBoolean();
            var message = new JOptionPane(
                    "Compiling starfarer.api.jar...",
                    JOptionPane.INFORMATION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION,
                    null,
                    new Object[]{CANCEL},
                    CANCEL
            );
            var dialog = message.createDialog(null, "Microforge");
            dialog.setModal(false);
            dialog.setAlwaysOnTop(true);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

            var compilationMessage = new CompilationMessage(dialog, closed);
            message.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, event -> {
                if (CANCEL.equals(event.getNewValue())) {
                    compilationMessage.cancel();
                }
            });
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    compilationMessage.cancel();
                }
            });

            result.set(compilationMessage);
            dialog.setVisible(true);
        });
        return result.get();
    }

    private void cancel() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        dialog.dispose();
        System.exit(1);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (dialog == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            dialog.dispose();
        } else {
            SwingUtilities.invokeLater(dialog::dispose);
        }
    }
}
