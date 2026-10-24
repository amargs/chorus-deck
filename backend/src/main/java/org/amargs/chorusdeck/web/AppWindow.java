package org.amargs.chorusdeck.web;

import ca.weblite.webview.WebViewPopupEvent;
import ca.weblite.webview.WebViewPopupHandler;
import ca.weblite.webview.swing.WebViewComponent;
import com.sun.jna.platform.win32.Kernel32;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Swing window with the page in WebView2. Returns when that window closes. */
public final class AppWindow {

    private static final Logger LOG = LogManager.getLogger(AppWindow.class);
    /** The native view appears on the first paint. This is how long that page may take to answer. */
    private static final long PAGE_WAIT_SECONDS = 3;

    private AppWindow() {
    }

    public static void open(URI uri, Path profile) throws Exception {
        if (!windows()) {
            AppDialog.error("O eCoral precisa do Windows.");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("eCoral's window needs a display.");
        }
        Path folder = profile.toAbsolutePath().normalize();
        Files.createDirectories(folder);
        try (Hold hold = Hold.tryAcquire(lockFile(folder))) {
            if (hold == null) {
                AppDialog.error("O eCoral já está aberto.");
                return;
            }
            // The native library reads this before it creates the WebView2 profile.
            // The install directory is often not writable.
            if (!Kernel32.INSTANCE.SetEnvironmentVariable("WEBVIEW2_USER_DATA_FOLDER", folder.toString())) {
                LOG.warn("WebView2 will use its own profile folder");
            }
            CountDownLatch closed = new CountDownLatch(1);
            AtomicReference<WebViewComponent> page = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> show(uri, closed, page));
            if (!pageAppeared(page.get())) {
                if (closed.getCount() > 0) {
                    SwingUtilities.invokeAndWait(() -> dispose(page.get()));
                    closed.await();
                    AppDialog.error("A janela do eCoral não abriu. Instale o Microsoft Edge WebView2 Runtime.");
                }
                return;
            }
            closed.await();
            LOG.info("Window closed");
        }
    }

    /** The page itself, or a blank document the engine uses while starting. */
    static boolean staysInApp(String url, URI app) {
        if (url == null || url.isBlank() || url.startsWith("about:")) {
            return true;
        }
        return sameServer(url, app);
    }

    /** A popup only when it loads this server. An empty window.open does not. */
    static boolean popupStays(String url, URI app) {
        return sameServer(url, app);
    }

    /** The shell's page has answered with its title. {@code evalAsync} returns that title as JSON. */
    static boolean pageReady(String title) {
        if (title == null || title.length() < 2 || !title.startsWith("\"") || !title.endsWith("\"")) {
            return false;
        }
        return "eCoral".equals(title.substring(1, title.length() - 1));
    }

    /** Lock beside the profile, in the eCoral state folder. */
    static Path lockFile(Path profile) {
        Path parent = profile.getParent();
        Path state = parent == null ? profile : parent;
        return state.resolve("window.lock");
    }

    private static void show(URI uri, CountDownLatch closed, AtomicReference<WebViewComponent> opened) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is enough
        }
        WebViewComponent page = WebViewComponent.create();
        page.setUrl(uri.toString());
        page.setNavigationHandler(event -> follow(event.url(), uri));
        page.setPopupHandler(new WebViewPopupHandler() {
            @Override
            public boolean popupRequested(WebViewPopupEvent event) {
                if (popupStays(event.targetUrl(), uri)) {
                    return true;
                }
                openOutside(event.targetUrl());
                return false;
            }
        });
        opened.set(page);
        JFrame frame = new JFrame("eCoral");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Image icon = loadIcon();
        if (icon != null) {
            frame.setIconImage(icon);
        }
        frame.add(page, BorderLayout.CENTER);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(Math.min(1280, Math.max(720, screen.width - 80)),
                Math.min(800, Math.max(480, screen.height - 80)));
        frame.setMinimumSize(new Dimension(720, 480));
        frame.setLocationRelativeTo(null);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closed.countDown();
            }
        });
        LOG.info("Opening " + uri);
        frame.setVisible(true);
    }

    private static boolean pageAppeared(WebViewComponent page) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PAGE_WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            AtomicReference<CompletableFuture<String>> pending = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> pending.set(page.evalAsync(
                    "return new Promise(resolve => {"
                            + " const check = () => document.title ? resolve(document.title) : setTimeout(check, 50);"
                            + " check();"
                            + "});")));
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            try {
                return pageReady(pending.get().get(left, TimeUnit.NANOSECONDS));
            } catch (TimeoutException timedOut) {
                return false;
            } catch (ExecutionException failed) {
                if (!notReady(failed)) {
                    LOG.warn("The page did not open: " + failed.getCause());
                    return false;
                }
                Thread.sleep(100);
            }
        }
        return false;
    }

    private static boolean notReady(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("not displayed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void dispose(WebViewComponent page) {
        java.awt.Window frame = SwingUtilities.getWindowAncestor(page);
        if (frame != null) {
            frame.dispose();
        }
    }

    /** Keep this server. Open another web page in the system browser. */
    private static boolean follow(String url, URI app) {
        if (staysInApp(url, app)) {
            return true;
        }
        openOutside(url);
        return false;
    }

    private static boolean sameServer(String url, URI app) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI target;
        try {
            target = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (target.getScheme() == null || !"http".equalsIgnoreCase(target.getScheme())) {
            return false;
        }
        String host = target.getHost();
        return host != null && host.equalsIgnoreCase(app.getHost()) && target.getPort() == app.getPort();
    }

    private static void openOutside(String url) {
        URI target;
        try {
            target = URI.create(url);
        } catch (IllegalArgumentException e) {
            return;
        }
        String scheme = target.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Desktop.getDesktop().browse(target);
            } catch (Exception e) {
                LOG.warn("Could not open " + target + ": " + e.getMessage());
            }
        }, "ecoral-link");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static Image loadIcon() {
        try (InputStream in = AppWindow.class.getResourceAsStream("/ecoral-icon.png")) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** One open window. A second launch in this process or another finds the file already locked. */
    static final class Hold implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private Hold(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        static Hold tryAcquire(Path file) throws IOException {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException already) {
                channel.close();
                return null;
            }
            if (lock == null) {
                channel.close();
                return null;
            }
            return new Hold(channel, lock);
        }

        @Override
        public void close() throws IOException {
            lock.release();
            channel.close();
        }
    }
}
