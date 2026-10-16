package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderChooserTest {

    @Test
    void aSecondCallerSharesTheOpenChoice() {
        FolderChooser.Flight flight = new FolderChooser.Flight();
        FolderChooser.Flight.Handle first = flight.acquire();
        FolderChooser.Flight.Handle second = flight.acquire();
        assertTrue(first.leader());
        assertFalse(second.leader());
        assertSame(first.result(), second.result());

        Path chosen = Path.of("C:/songs");
        first.result().complete(chosen);
        flight.finish(first.result());

        assertEquals(chosen, second.result().join());
        assertTrue(flight.acquire().leader());
    }

    @Test
    void aCancelledChoiceIsSharedAsNoDirectory() {
        FolderChooser.Flight flight = new FolderChooser.Flight();
        FolderChooser.Flight.Handle first = flight.acquire();
        FolderChooser.Flight.Handle second = flight.acquire();
        first.result().complete(null);
        flight.finish(first.result());

        assertNull(second.result().join());
        assertTrue(flight.acquire().leader());
    }
}
