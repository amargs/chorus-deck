package org.amargs.chorusdeck;

import org.amargs.chorusdeck.cli.Cli;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        try {
            Cli.run(args);
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage());
            System.exit(1);
        }
    }
}
