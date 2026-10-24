package org.amargs.chorusdeck.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** {@code index.json} and {@code liturgy.json} packaged with the app. */
public final class PackagedSeed implements LibrarySeed {

    static final String INDEX = "/seed/index.json";
    static final String LITURGY = "/seed/liturgy.json";

    public static final PackagedSeed INSTANCE = new PackagedSeed();

    private PackagedSeed() {
    }

    @Override
    public void copyIndex(Path destination) throws IOException {
        copy(INDEX, destination, "The app is missing the song index.");
    }

    @Override
    public void copyLiturgy(Path destination) throws IOException {
        copy(LITURGY, destination, "The app is missing the liturgy.");
    }

    private static void copy(String resource, Path destination, String missing) throws IOException {
        try (InputStream in = PackagedSeed.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException(missing);
            }
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
