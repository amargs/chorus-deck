package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowCloseTest {

    @Test
    void aTopLevelWindowCountsAndAToolWindowDoesNot() {
        assertTrue(WindowClose.isTopLevelAppWindow(true, false, 0, 0));
        assertFalse(WindowClose.isTopLevelAppWindow(false, false, 0, 0));
        assertFalse(WindowClose.isTopLevelAppWindow(true, true, 0, 0));
        assertFalse(WindowClose.isTopLevelAppWindow(true, false, 0x40000000L, 0));
        assertFalse(WindowClose.isTopLevelAppWindow(true, false, 0, 0x80L));
    }

    @Test
    void theCloseStandsOnlyAfterTheLastWindowStaysGone() {
        WindowClose.Track track = new WindowClose.Track();
        assertEquals(-1, track.closed(1));
        int opened = track.opened(1);
        assertFalse(track.due(opened));
        track.opened(2);
        assertEquals(-1, track.closed(1));
        int token = track.closed(2);
        assertTrue(token >= 0);
        assertTrue(track.due(token));
        int again = track.opened(3);
        assertFalse(track.due(token));
        assertFalse(track.due(again));
    }

    @Test
    void aChildProcessBelongsToTheLaunchThatStartedIt() throws Exception {
        Process child = new ProcessBuilder("cmd.exe", "/c", "ping", "-n", "4", "127.0.0.1").start();
        try {
            assertTrue(WindowClose.belongsToLaunch(child.pid(), ProcessHandle.current().pid()));
            assertFalse(WindowClose.belongsToLaunch(ProcessHandle.current().pid(), child.pid()));
        } finally {
            child.destroyForcibly();
        }
    }
}
