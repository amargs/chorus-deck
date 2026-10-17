package org.amargs.chorusdeck.web;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WinEventProc;
import com.sun.jna.ptr.IntByReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Waits until the browser window closes, or that browser process exits. */
final class WindowClose {

    private static final Logger LOG = LogManager.getLogger(WindowClose.class);
    private static final int EVENT_OBJECT_CREATE = 0x8000;
    private static final int EVENT_OBJECT_DESTROY = 0x8001;
    private static final int EVENT_OBJECT_SHOW = 0x8002;
    private static final int EVENT_OBJECT_HIDE = 0x8003;
    private static final int WINEVENT_SKIPOWNPROCESS = 0x0002;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WM_QUIT = 0x0012;
    /** A replacement window during startup must appear before this, or the close stands. */
    private static final int GRACE_MS = 500;

    private WindowClose() {
    }

    static void await(Process process) throws InterruptedException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            process.waitFor();
            return;
        }
        listen(process);
    }

    /** True when {@code pid} is {@code root} or a process {@code root} started. */
    static boolean belongsToLaunch(long pid, long root) {
        long current = pid;
        for (int depth = 0; depth < 8 && current > 0; depth++) {
            if (current == root) {
                return true;
            }
            Optional<ProcessHandle> handle = ProcessHandle.of(current);
            if (handle.isEmpty()) {
                return false;
            }
            Optional<ProcessHandle> parent = handle.get().parent();
            if (parent.isEmpty()) {
                return false;
            }
            current = parent.get().pid();
        }
        return false;
    }

    /** A visible top-level window, not a tool or child window. */
    static boolean isTopLevelAppWindow(boolean visible, boolean owned, long style, long exStyle) {
        boolean child = (style & WinUser.WS_CHILD) != 0;
        boolean tool = (exStyle & WS_EX_TOOLWINDOW) != 0;
        return visible && !owned && !child && !tool;
    }

    private static void listen(Process process) throws InterruptedException {
        long root = process.pid();
        Track track = new Track();
        ScheduledExecutorService grace = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ecoral-window");
            thread.setDaemon(true);
            return thread;
        });
        MSG msg = new MSG();
        User32.INSTANCE.PeekMessage(msg, null, 0, 0, 0);
        int threadId = Kernel32.INSTANCE.GetCurrentThreadId();
        WinEventProc proc = (hook, event, hwnd, idObject, idChild, eventThread, time) ->
                onEvent(root, track, grace, threadId, event, hwnd, idObject, idChild);
        HANDLE events = User32.INSTANCE.SetWinEventHook(
                EVENT_OBJECT_CREATE,
                EVENT_OBJECT_HIDE,
                null,
                proc,
                0,
                0,
                WINEVENT_SKIPOWNPROCESS);
        if (events == null) {
            grace.shutdownNow();
            LOG.warn("Window close hook was not installed; waiting for the browser process");
            process.waitFor();
            return;
        }
        Thread exit = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, new WPARAM(0), new LPARAM(0));
        }, "ecoral-browser-exit");
        exit.setDaemon(true);
        exit.start();
        try {
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
            LOG.info("Browser window closed");
        } finally {
            exit.interrupt();
            User32.INSTANCE.UnhookWinEvent(events);
            grace.shutdownNow();
        }
    }

    private static void onEvent(long root, Track track, ScheduledExecutorService grace, int threadId,
                                DWORD event, HWND hwnd, com.sun.jna.platform.win32.WinDef.LONG idObject,
                                com.sun.jna.platform.win32.WinDef.LONG idChild) {
        try {
            if (hwnd == null || idObject.longValue() != 0 || idChild.longValue() != 0) {
                return;
            }
            long key = Pointer.nativeValue(hwnd.getPointer());
            int kind = event.intValue();
            if (kind == EVENT_OBJECT_DESTROY || kind == EVENT_OBJECT_HIDE) {
                int token = track.closed(key);
                if (token >= 0) {
                    grace.schedule(() -> finish(track, threadId, token), GRACE_MS, TimeUnit.MILLISECONDS);
                }
                return;
            }
            if ((kind == EVENT_OBJECT_SHOW || kind == EVENT_OBJECT_CREATE) && qualifies(hwnd, root)) {
                track.opened(key);
            }
        } catch (RuntimeException e) {
            LOG.warn("Ignoring a window event: " + e.getMessage());
        }
    }

    private static void finish(Track track, int threadId, int token) {
        if (track.due(token)) {
            User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, new WPARAM(0), new LPARAM(0));
        }
    }

    private static boolean qualifies(HWND hwnd, long root) {
        LONG_PTR style = User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_STYLE);
        LONG_PTR exStyle = User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE);
        HWND owner = User32.INSTANCE.GetWindow(hwnd, new DWORD(WinUser.GW_OWNER));
        if (!isTopLevelAppWindow(User32.INSTANCE.IsWindowVisible(hwnd), owner != null,
                style.longValue(), exStyle.longValue())) {
            return false;
        }
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        return belongsToLaunch(Integer.toUnsignedLong(pid.getValue()), root);
    }

    /** Windows eCoral opened, and the generation of the latest change. */
    static final class Track {
        private final Set<Long> open = new HashSet<>();
        private boolean seen;
        private int generation;

        synchronized int opened(long hwnd) {
            open.add(hwnd);
            seen = true;
            return ++generation;
        }

        /** Generation to confirm after the grace period, or -1 when windows remain. */
        synchronized int closed(long hwnd) {
            if (!open.remove(hwnd) || !seen || !open.isEmpty()) {
                return -1;
            }
            return ++generation;
        }

        synchronized boolean due(int token) {
            return token == generation && seen && open.isEmpty();
        }
    }
}
