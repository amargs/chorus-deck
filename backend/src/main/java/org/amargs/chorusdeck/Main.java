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
        String season = null;
        String cover = null;
        String domingo = null;
        String frase = null;
        String versiculo = null;
        String ano = null;

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
                    requireGenerate(command, "--ids");
                    if (ids != null) {
                        throw new IllegalArgumentException("Duplicate --ids.\n" + usage());
                    }
                    ids = parseIds(requireValue(args, ++i, "--ids"));
                }
                case "--season" -> {
                    requireGenerate(command, "--season");
                    season = single(season, requireValue(args, ++i, "--season"), "--season");
                }
                case "--cover" -> {
                    requireGenerate(command, "--cover");
                    cover = single(cover, requireValue(args, ++i, "--cover"), "--cover");
                }
                case "--domingo" -> {
                    requireGenerate(command, "--domingo");
                    domingo = single(domingo, requireValue(args, ++i, "--domingo"), "--domingo");
                }
                case "--frase" -> {
                    requireGenerate(command, "--frase");
                    frase = single(frase, requireValue(args, ++i, "--frase"), "--frase");
                }
                case "--versiculo" -> {
                    requireGenerate(command, "--versiculo");
                    versiculo = single(versiculo, requireValue(args, ++i, "--versiculo"), "--versiculo");
                }
                case "--ano" -> {
                    requireGenerate(command, "--ano");
                    ano = single(ano, requireValue(args, ++i, "--ano"), "--ano");
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
                if (season == null || domingo == null || frase == null || versiculo == null || ano == null) {
                    throw new IllegalArgumentException(
                            "generate requires --season, --ano, --domingo, --frase, and --versiculo.\n" + usage());
                }
                new DeckGenerator().generate(config, ids, new DeckGenerator.Cover(
                        season, cover, domingo, frase, versiculo, Season.ano(ano)));
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command + "\n" + usage());
        }
    }

    private static void requireGenerate(String command, String flag) {
        if (!"generate".equals(command)) {
            throw new IllegalArgumentException(flag + " is only valid for generate.\n" + usage());
        }
    }

    private static String single(String current, String value, String flag) {
        if (current != null) {
            throw new IllegalArgumentException("Duplicate " + flag + ".\n" + usage());
        }
        return value;
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
                  generate --ids <id,id,...> --season <season> --ano <A|B|C> --domingo <text> --frase <text> --versiculo <text> [--cover <layout>] [--config <file>]""";
    }
}
