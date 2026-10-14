package org.amargs.chorusdeck;

import org.amargs.chorusdeck.controller.Cli;

public class Main {

    public static void main(String[] args) throws Exception {
        try {
            Cli.run(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
