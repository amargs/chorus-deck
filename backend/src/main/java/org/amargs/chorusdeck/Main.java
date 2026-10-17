package org.amargs.chorusdeck;

import org.amargs.chorusdeck.cli.Cli;
import org.amargs.chorusdeck.web.AppDialog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        try {
            Cli.run(args);
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage());
            stop(args, e.getMessage(), true);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.toString();
            }
            LOG.error(message, e);
            stop(args, message, false);
            throw e;
        }
    }

    /** A packaged serve has no console, so the message is a dialog and the process ends. */
    private static void stop(String[] args, String message, boolean exit) {
        if (args.length > 0 && "serve".equals(args[0])) {
            AppDialog.error(message);
            System.exit(1);
        }
        if (exit) {
            System.exit(1);
        }
    }
}
