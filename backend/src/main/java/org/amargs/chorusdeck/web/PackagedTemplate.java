package org.amargs.chorusdeck.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** The slide template shipped inside the app. */
public final class PackagedTemplate {

    static final String RESOURCE = "/template_4x3.pptx";

    private static volatile Path cached;

    private PackagedTemplate() {
    }

    public static Path file() throws IOException {
        Path existing = cached;
        if (existing != null && Files.isRegularFile(existing)) {
            return existing;
        }
        synchronized (PackagedTemplate.class) {
            if (cached != null && Files.isRegularFile(cached)) {
                return cached;
            }
            try (InputStream in = PackagedTemplate.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("The app is missing template_4x3.pptx.");
                }
                Path temp = Files.createTempFile("chorus-deck-template", ".pptx");
                temp.toFile().deleteOnExit();
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                cached = temp;
                return temp;
            }
        }
    }
}
