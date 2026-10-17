package org.amargs.chorusdeck.web;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;

/** A message for the packaged app, which has no console. */
public final class AppDialog {

    private AppDialog() {
    }

    public static void error(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        String text = message == null || message.isBlank() ? "O eCoral parou." : message;
        try {
            JOptionPane.showMessageDialog(null, text, "eCoral", JOptionPane.ERROR_MESSAGE);
        } catch (HeadlessException ignored) {
            // A headless test or service has nowhere to show the dialog.
        }
    }
}
