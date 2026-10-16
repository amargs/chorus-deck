package org.amargs.chorusdeck.web;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Native directory dialog. The Swing dialog itself is not used from tests. */
public final class FolderChooser {

    private static final Flight FLIGHT = new Flight();
    private static volatile JDialog openDialog;

    private FolderChooser() {
    }

    /** @return the chosen directory, or null when the dialog is cancelled */
    public static Path choose(Path start) throws Exception {
        return choose(start, true, "Escolher pasta");
    }

    /** @return the chosen file, or null when the dialog is cancelled */
    public static Path chooseFile(Path start) throws Exception {
        return choose(start, false, "Escolher índice");
    }

    private static Path choose(Path start, boolean directoriesOnly, String title) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A folder chooser needs a display.");
        }
        Flight.Handle handle = FLIGHT.acquire();
        if (!handle.leader()) {
            raiseOpen();
            return await(handle.result());
        }
        try {
            Path chosen = show(start, directoriesOnly, title);
            handle.result().complete(chosen);
            return chosen;
        } catch (Throwable error) {
            handle.result().completeExceptionally(error);
            if (error instanceof Exception exception) {
                throw exception;
            }
            throw error;
        } finally {
            FLIGHT.finish(handle.result());
        }
    }

    private static Path await(CompletableFuture<Path> result) throws Exception {
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failed;
        }
    }

    private static Path show(Path start, boolean directoriesOnly, String title) throws Exception {
        Path[] chosen = new Path[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame owner = new JFrame();
            owner.setAlwaysOnTop(true);
            owner.setUndecorated(true);
            owner.setType(Window.Type.UTILITY);
            owner.setSize(0, 0);
            owner.setLocationRelativeTo(null);
            owner.setVisible(true);
            try {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // the default look is enough
                }
                JFileChooser chooser = new JFileChooser() {
                    @Override
                    protected JDialog createDialog(Component parent) throws HeadlessException {
                        JDialog dialog = super.createDialog(parent);
                        dialog.setAlwaysOnTop(true);
                        dialog.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowOpened(WindowEvent event) {
                                focus(dialog);
                                SwingUtilities.invokeLater(() -> focus(dialog));
                            }
                        });
                        openDialog = dialog;
                        return dialog;
                    }
                };
                chooser.setDialogTitle(title);
                if (directoriesOnly) {
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if (start != null && Files.isDirectory(start)) {
                        chooser.setCurrentDirectory(start.toFile());
                    }
                } else {
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    chooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
                    Path folder = start != null && Files.isDirectory(start) ? start : start == null ? null : start.getParent();
                    if (folder != null && Files.isDirectory(folder)) {
                        chooser.setCurrentDirectory(folder.toFile());
                    }
                }
                focus(owner);
                if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    chosen[0] = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
                }
            } finally {
                openDialog = null;
                owner.dispose();
            }
        });
        return chosen[0];
    }

    /** Brings the dialog that is already open to the front. */
    static void raiseOpen() {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = openDialog;
            if (dialog != null) {
                focus(dialog);
            }
        });
    }

    private static void focus(Window window) {
        window.setAlwaysOnTop(true);
        window.toFront();
        window.requestFocus();
    }

    /** One folder dialog at a time. A later caller waits on the same result. */
    static final class Flight {
        private CompletableFuture<Path> pending;

        synchronized Handle acquire() {
            boolean leader = pending == null;
            if (leader) {
                pending = new CompletableFuture<>();
            }
            return new Handle(leader, pending);
        }

        synchronized void finish(CompletableFuture<Path> mine) {
            if (pending == mine) {
                pending = null;
            }
        }

        record Handle(boolean leader, CompletableFuture<Path> result) {
        }
    }
}
