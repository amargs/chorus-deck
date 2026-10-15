package org.amargs.chorusdeck.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTableTest {

    @Test
    void padsColumnsToTheWidestCell() {
        assertEquals(List.of(
                "| songbook id | songbook title    | slide id | slide title              |",
                "|-------------|-------------------|----------|--------------------------|",
                "| 147         | Quero ser como tu | 134      | Quero ser como tu, Maria |"),
                TextTable.format(
                        List.of(List.of("147", "Quero ser como tu", "134", "Quero ser como tu, Maria")),
                        "songbook id", "songbook title", "slide id", "slide title"));
    }
}
