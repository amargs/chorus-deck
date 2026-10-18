package org.amargs.chorusdeck.web;

import ca.weblite.webview.WebViewPopupEvent;
import ca.weblite.webview.WebViewPopupHandler;
import ca.weblite.webview.swing.WebViewComponent;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinReg;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/** Swing window with the page in WebView2. Returns when that window closes. */
public final class AppWindow {

    private static final Logger LOG = LogManager.getLogger(AppWindow.class);
    /** Evergreen WebView2 Runtime client, from Microsoft's distribution docs. */
    private static final String RUNTIME_CLIENT = "{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}";
    private static final String RUNTIME_KEY =
            "SOFTWARE\\Microsoft\\EdgeUpdate\\Clients\\" + RUNTIME_CLIENT;
    private static final String RUNTIME_KEY_WOW =
            "SOFTWARE\\WOW6432Node\\Microsoft\\EdgeUpdate\\Clients\\" + RUNTIME_CLIENT;

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
        if (!runtimeInstalled()) {
            AppDialog.error("Instale o Microsoft Edge WebView2 Runtime para abrir o eCoral.");
            return;
        }
        Path folder = profile.toAbsolutePath().normalize();
        Files.createDirectories(folder);
        // The native library reads this before it creates the WebView2 profile.
        // The install directory is often not writable.
        if (!Kernel32.INSTANCE.SetEnvironmentVariable("WEBVIEW2_USER_DATA_FOLDER", folder.toString())) {
            LOG.warn("WebView2 will use its own profile folder");
        }
        CountDownLatch closed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> show(uri, closed));
        closed.await();
        LOG.info("Window closed");
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

    /** True when an Evergreen runtime executable or its registry client is present. */
    static boolean runtimeInstalled(List<Path> executables, boolean registryHit) {
        for (Path executable : executables) {
            if (executable != null && Files.isRegularFile(executable)) {
                return true;
            }
        }
        return registryHit;
    }

    private static void show(URI uri, CountDownLatch closed) {
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
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closed.countDown();
            }
        });
        LOG.info("Opening " + uri);
        frame.setVisible(true);
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

    private static boolean runtimeInstalled() {
        return runtimeInstalled(runtimeExecutables(), runtimeRegistered());
    }

    private static List<Path> runtimeExecutables() {
        List<Path> found = new ArrayList<>();
        addRuntime(found, System.getenv("ProgramFiles(x86)"));
        addRuntime(found, System.getenv("ProgramFiles"));
        addRuntime(found, System.getenv("LOCALAPPDATA"));
        return found;
    }

    private static void addRuntime(List<Path> found, String root) {
        if (root == null || root.isBlank()) {
            return;
        }
        Path application = Path.of(root, "Microsoft", "EdgeWebView", "Application");
        if (!Files.isDirectory(application)) {
            return;
        }
        try (var children = Files.newDirectoryStream(application)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    found.add(child.resolve("msedgewebview2.exe"));
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not look for the WebView2 runtime: " + e.getMessage());
        }
    }

    private static boolean runtimeRegistered() {
        return hasVersion(WinReg.HKEY_LOCAL_MACHINE, RUNTIME_KEY)
                || hasVersion(WinReg.HKEY_LOCAL_MACHINE, RUNTIME_KEY_WOW)
                || hasVersion(WinReg.HKEY_CURRENT_USER, RUNTIME_KEY);
    }

    private static boolean hasVersion(WinReg.HKEY root, String key) {
        try {
            if (!Advapi32Util.registryKeyExists(root, key) || !Advapi32Util.registryValueExists(root, key, "pv")) {
                return false;
            }
            String version = Advapi32Util.registryGetStringValue(root, key, "pv");
            return version != null && !version.isBlank();
        } catch (RuntimeException e) {
            return false;
        }
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
}
