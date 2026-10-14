package org.amargs.chorusdeck.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {

    @Test
    void missingBoldWeightDefaultsToSemibold() throws Exception {
        Path file = Files.createTempFile("chorus-deck-config", ".json");
        Files.writeString(file, """
                {"sourcePptx":"a.pptx","indexFile":"b.json","template4x3":"c.pptx","outputFile":"d.pptx"}
                """);
        assertEquals("semibold", AppConfig.load(file).boldWeight());
    }
}
