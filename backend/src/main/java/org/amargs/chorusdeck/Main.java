package org.amargs.chorusdeck;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException(usage());
        }

        String command = args[0];
        Path configArg = null;
        List<Integer> ids = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--config" -> {
                    if (configArg != null) {
                        throw new IllegalArgumentException("Duplicate --config.\n" + usage());
                    }
                    configArg = Path.of(requireValue(args, ++i, "--config"));
                }
                case "--ids" -> {
                    if (!"generate".equals(command)) {
                        throw new IllegalArgumentException("--ids is only valid for generate.\n" + usage());
                    }
                    if (ids != null) {
                        throw new IllegalArgumentException("Duplicate --ids.\n" + usage());
                    }
                    ids = parseIds(requireValue(args, ++i, "--ids"));
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + arg + "\n" + usage());
            }
        }

        AppConfig config = AppConfig.load(AppConfig.findConfig(configArg));
        switch (command) {
            case "index" -> new SongIndexer().rebuild(config);
            case "generate" -> {
                if (ids == null) {
                    throw new IllegalArgumentException("generate requires --ids.\n" + usage());
                }
                new DeckGenerator().generate(config, ids);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command + "\n" + usage());
        }
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(flag + " requires a value.\n" + usage());
        }
        return args[index];
    }

    private static List<Integer> parseIds(String raw) {
        String[] parts = raw.split(",");
        List<Integer> ids = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty song id in --ids.\n" + usage());
            }
            try {
                int id = Integer.parseInt(token);
                if (id < 1) {
                    throw new IllegalArgumentException("Song ids start at 1.\n" + usage());
                }
                ids.add(id);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a song id: " + token + "\n" + usage());
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("generate requires --ids.\n" + usage());
        }
        return ids;
    }

    private static String usage() {
        return """
                Usage:
                  index [--config <file>]
                  generate --ids <id,id,...> [--config <file>]""";
    }
}
