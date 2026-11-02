package net.minecraftforge.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;

class WrapperInstallerTest {
    private static final class FakeProcess extends Process {
        private final PipedInputStream processOutput = new PipedInputStream();
        private final PipedOutputStream output;
        private final CountDownLatch exited = new CountDownLatch(1);
        private volatile int exitCode;
        private boolean finished;

        private FakeProcess() throws IOException {
            output = new PipedOutputStream(processOutput);
        }

        synchronized void writeOutput(String text) throws IOException {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        synchronized void finish(int result) throws IOException {
            if (!finished) {
                finished = true;
                exitCode = result;
                try {
                    output.close();
                } finally {
                    exited.countDown();
                }
            }
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public PipedInputStream getInputStream() {
            return processOutput;
        }

        @Override
        public ByteArrayInputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() throws InterruptedException {
            exited.await();
            return exitCode;
        }

        @Override
        public int exitValue() {
            if (exited.getCount() != 0) {
                throw new IllegalThreadStateException("Process has not exited");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            try {
                finish(1);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    @Test
    void mapsNeoForgeVersionToMinecraftVersion() {
        assertEquals("1.21.1", WrapperInstaller.minecraftVersion("1.21.1-2.0.0"));
        assertEquals("26.3", WrapperInstaller.minecraftVersion("26.3.0.95"));
    }

    @Test
    void installsAutomodpackOnlyForGameInstallations() {
        assertTrue(WrapperInstaller.installsGame("--installClient"));
        assertTrue(WrapperInstaller.installsGame("--installServer"));
        assertFalse(WrapperInstaller.installsGame("--fat-installer"));
    }

    @Test
    void skipsNeoForgeForExistingLauncherProfiles() {
        assertFalse(WrapperInstaller.shouldInstallNeoForge(true));
        assertTrue(WrapperInstaller.shouldInstallNeoForge(false));
    }

    @Test
    void explainsMissingMinecraftInstallation() throws Exception {
        File parent = java.nio.file.Files.createTempDirectory("installer-missing-installation").toFile();
        File target = new File(parent, "minecraft");
        try {
            assertFalse(WrapperInstaller.hasMinecraftLauncherProfile(target));
            assertEquals("There is no minecraft installation at: " + target,
                    WrapperInstaller.clientInstallPreflightError(target));
        } finally {
            parent.delete();
        }
    }

    @Test
    void explainsExistingDirectoryWithoutMinecraftLauncherProfile() throws Exception {
        File target = java.nio.file.Files.createTempDirectory("installer-no-launcher-profile").toFile();
        try {
            assertFalse(WrapperInstaller.hasMinecraftLauncherProfile(target));
            assertEquals("There is no minecraft launcher profile in \"" + target
                    + "\", you need to run the launcher first!",
                    WrapperInstaller.clientInstallPreflightError(target));
        } finally {
            target.delete();
        }
    }

    @Test
    void namesAppearanceThemes() {
        assertEquals("Light", WrapperInstaller.themeName(WrapperInstaller.Theme.LIGHT));
        assertEquals("Dark", WrapperInstaller.themeName(WrapperInstaller.Theme.DARK));
    }

    @Test
    void defaultsToDarkTheme() {
        assertEquals(WrapperInstaller.Theme.DARK, WrapperInstaller.defaultTheme());
    }

    @Test
    void aboutPanelDisplaysVersionAndOpensItsProjectLinks() throws Exception {
        List<URI> openedLinks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        assertEquals("https://github.com/neoforged/LegacyInstaller", WrapperInstaller.aboutLinks().get(1).url());
        assertEquals("https://github.com/Skidamek/AutoModpack-Installer", WrapperInstaller.aboutLinks().get(2).url());
        SwingUtilities.invokeAndWait(() -> {
            JMenuItem aboutItem = WrapperInstaller.createMenuBar(null).getMenu(2).getItem(0);
            assertEquals("About", aboutItem.getText());
            JPanel panel = WrapperInstaller.createAboutPanel(openedLinks::add, errors::add);
            assertEquals("Version: " + WrapperInstaller.applicationVersion(),
                    ((JLabel) panel.getComponent(1)).getText());

            List<JButton> linkButtons = buttonsIn(panel);
            assertEquals(WrapperInstaller.aboutLinks().size(), linkButtons.size());
            for (int index = 0; index < linkButtons.size(); index++) {
                JButton button = linkButtons.get(index);
                WrapperInstaller.AboutLink link = WrapperInstaller.aboutLinks().get(index);
                assertEquals(link.label(), button.getText());
                assertEquals(link.url(), button.getClientProperty("WrapperInstaller.aboutUrl"));
                assertEquals(link.url(), button.getToolTipText());
                button.doClick();
            }
        });

        assertEquals(WrapperInstaller.aboutLinks().stream().map(link -> URI.create(link.url())).toList(), openedLinks);
        assertTrue(errors.isEmpty());
    }

    @Test
    void aboutVersionUsesDevelopmentFallbackWhenManifestVersionIsUnavailable() {
        assertEquals("Development build", WrapperInstaller.applicationVersion(null));
        assertEquals("Development build", WrapperInstaller.applicationVersion(" "));
        assertEquals("1.2.3", WrapperInstaller.applicationVersion("1.2.3"));
    }

    @Test
    void aboutPanelUsesTheActiveLightAndDarkTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = WrapperInstaller.createAboutPanel(uri -> { }, message -> { });
            for (WrapperInstaller.Theme theme : new WrapperInstaller.Theme[] {
                    WrapperInstaller.Theme.DARK, WrapperInstaller.Theme.LIGHT}) {
                WrapperInstaller.applyComponentTheme(panel, theme);
                Color background = theme == WrapperInstaller.Theme.DARK
                        ? new Color(43, 43, 43) : Color.WHITE;
                Color foreground = theme == WrapperInstaller.Theme.DARK
                        ? new Color(235, 235, 235) : Color.BLACK;
                Color control = theme == WrapperInstaller.Theme.DARK
                        ? new Color(65, 65, 65) : new Color(238, 238, 238);

                assertEquals(background, panel.getBackground());
                assertEquals(foreground, ((JLabel) panel.getComponent(0)).getForeground());
                assertEquals(control, buttonsIn(panel).get(0).getBackground());
            }
        });
    }

    @Test
    void aboutLinkReportsBrowserFailuresWithTheUrl() {
        List<String> errors = new ArrayList<>();
        String url = "https://example.test/download";

        assertFalse(WrapperInstaller.openAboutLink(url,
                uri -> { throw new IOException("Browser unavailable"); }, errors::add));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains(url));
        assertTrue(errors.get(0).contains("Open it manually in your browser."));
        assertTrue(errors.get(0).contains("Browser unavailable"));
    }

    @Test
    void themesSettingsAndNestedMenusAcrossThemeChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JMenuBar menuBar = WrapperInstaller.createMenuBar(null);
            JMenu settings = menuBar.getMenu(1);
            JMenu appearance = (JMenu) settings.getPopupMenu().getComponent(0);
            JMenu help = menuBar.getMenu(2);
            JPopupMenu settingsPopup = settings.getPopupMenu();
            JPopupMenu appearancePopup = appearance.getPopupMenu();
            JPopupMenu helpPopup = help.getPopupMenu();
            JMenuItem light = (JMenuItem) appearancePopup.getComponent(0);
            JMenuItem dark = (JMenuItem) appearancePopup.getComponent(1);

            for (WrapperInstaller.Theme theme : new WrapperInstaller.Theme[] {
                    WrapperInstaller.Theme.DARK, WrapperInstaller.Theme.LIGHT, WrapperInstaller.Theme.DARK}) {
                WrapperInstaller.applyComponentTheme(menuBar, theme);
                Color background = theme == WrapperInstaller.Theme.DARK
                        ? new Color(43, 43, 43) : Color.WHITE;
                Color foreground = theme == WrapperInstaller.Theme.DARK
                        ? new Color(235, 235, 235) : Color.BLACK;
                Color selectionBackground = theme == WrapperInstaller.Theme.DARK
                        ? new Color(90, 105, 120) : new Color(184, 207, 229);
                Color selectionForeground = theme == WrapperInstaller.Theme.DARK ? Color.WHITE : Color.BLACK;

                assertEquals(background, settingsPopup.getBackground());
                assertEquals(foreground, settingsPopup.getForeground());
                assertEquals(background, appearancePopup.getBackground());
                assertEquals(foreground, appearancePopup.getForeground());
                assertEquals(background, helpPopup.getBackground());
                assertEquals(foreground, light.getForeground());
                assertEquals(foreground, dark.getForeground());
                assertEquals(selectionBackground, UIManager.getColor("Menu.selectionBackground"));
                assertEquals(selectionForeground, UIManager.getColor("Menu.selectionForeground"));
                assertEquals(selectionBackground, UIManager.getColor("MenuItem.selectionBackground"));
                assertEquals(selectionForeground, UIManager.getColor("MenuItem.selectionForeground"));
                assertEquals(selectionBackground, UIManager.getColor("RadioButtonMenuItem.selectionBackground"));
                assertEquals(selectionForeground, UIManager.getColor("RadioButtonMenuItem.selectionForeground"));
            }
        });
    }

    @Test
    void providesDirectLauncherInstallerEndpoints() {
        assertEquals("https://download.overwolf.com/install/Download?Name=CurseForge&ExtensionId=cfiahnpaolfnlgaihhmobmnjdafknjnjdpdabpcm",
                WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.CURSEFORGE));
        assertTrue(WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.MODRINTH)
                .startsWith("https://api.github.com/repos/modrinth/theseus/releases/latest"));
        assertTrue(WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.CURSEFORGE)
                .startsWith("https://download.overwolf.com/install/Download?"));
        assertFalse(WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.MODRINTH)
                .contains("modrinth.com/app"));
    }

    @Test
    void identifiesInstallerSuffixesWithoutOpeningPages() {
        assertEquals(".exe", WrapperInstaller.installerSuffix("application/octet-stream", "https://example.test/app.exe"));
        assertEquals(".dmg", WrapperInstaller.installerSuffix("application/octet-stream", "https://example.test/app.dmg"));
        assertEquals(".AppImage", WrapperInstaller.installerSuffix("application/octet-stream", "https://example.test/app.AppImage"));
        assertEquals(".zip", WrapperInstaller.installerSuffix("application/zip", "https://example.test/app"));
    }

    @Test
    void exposesModrinthReleaseApiInsteadOfWebsite() {
        assertEquals("https://api.github.com/repos/modrinth/theseus/releases/latest",
                WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.MODRINTH));
    }

    @Test
    void streamsInstallerOutputAndWaitsForTheProcessToExit() throws Exception {
        FakeProcess process = new FakeProcess();
        List<String> output = new ArrayList<>();
        CountDownLatch linesReceived = new CountDownLatch(1);
        FutureTask<WrapperInstaller.InstallerLaunchResult> launch = new FutureTask<>(() ->
                WrapperInstaller.launchInstaller(Path.of("installer.exe"), true, ignored -> process,
                        ignored -> { throw new AssertionError("Desktop opener should not be used"); },
                        line -> {
                            output.add(line);
                            linesReceived.countDown();
                        }));
        Thread worker = new Thread(launch);
        worker.start();

        try {
            process.writeOutput("Preparing installation...\n");
            assertTrue(linesReceived.await(5, TimeUnit.SECONDS), "Installer output was not streamed");
            assertFalse(launch.isDone(), "The operation finished while the installer process was still running");

            process.writeOutput("Writing files...\n");
            process.finish(0);
            WrapperInstaller.InstallerLaunchResult result = launch.get(5, TimeUnit.SECONDS);

            assertEquals(List.of("Preparing installation...", "Writing files..."), output);
            assertEquals(WrapperInstaller.InstallerLaunchType.PROCESS_EXITED, result.type());
            assertEquals(0, result.exitCode());
            assertEquals(WrapperInstaller.StatusBox.State.UNVERIFIED,
                    WrapperInstaller.launcherInstallStatus(result));
            assertTrue(WrapperInstaller.launcherInstallMessage(WrapperInstaller.LauncherApp.CURSEFORGE, result)
                    .contains("could not be verified"));
        } finally {
            process.finish(0);
            worker.join(5000);
        }
    }

    @Test
    void reportsNonzeroInstallerExitAsFailure() throws Exception {
        FakeProcess process = new FakeProcess();
        process.writeOutput("Installer failed\n");
        process.finish(23);

        WrapperInstaller.InstallerLaunchResult result = WrapperInstaller.launchInstaller(
                Path.of("installer.exe"), true, ignored -> process,
                ignored -> { throw new AssertionError("Desktop opener should not be used"); }, ignored -> { });

        assertEquals(23, result.exitCode());
        assertEquals(WrapperInstaller.StatusBox.State.FAILURE, WrapperInstaller.launcherInstallStatus(result));
        assertTrue(WrapperInstaller.launcherInstallMessage(WrapperInstaller.LauncherApp.MODRINTH, result)
                .contains("code 23"));
    }

    @Test
    void reportsDesktopOpenedInstallerAsUnmonitored() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean opened = new java.util.concurrent.atomic.AtomicBoolean();

        WrapperInstaller.InstallerLaunchResult result = WrapperInstaller.launchInstaller(
                Path.of("installer.dmg"), false,
                ignored -> { throw new AssertionError("A process should not be started on this platform"); },
                ignored -> opened.set(true), ignored -> { });

        assertTrue(opened.get());
        assertEquals(WrapperInstaller.InstallerLaunchType.OPENED_UNMONITORED, result.type());
        assertEquals(WrapperInstaller.StatusBox.State.UNVERIFIED, WrapperInstaller.launcherInstallStatus(result));
        assertTrue(WrapperInstaller.launcherInstallMessage(WrapperInstaller.LauncherApp.MODRINTH, result)
                .contains("cannot be monitored"));
    }

    @Test
    void configuresCurseForgeDownloadAsBrowserRequest() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://example.test/download").openConnection();
        WrapperInstaller.configureLauncherDownloadConnection(connection, WrapperInstaller.LauncherApp.CURSEFORGE);

        assertTrue(connection.getRequestProperty("User-Agent").startsWith("Mozilla/5.0"));
        assertTrue(connection.getRequestProperty("Accept").contains("application/octet-stream"));
        assertEquals("https://www.curseforge.com/", connection.getRequestProperty("Referer"));
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"));
    }

    @Test
    void recognizesWindowsPortableExecutableHeaders() throws Exception {
        Path valid = Files.createTempFile("curseforge-valid", ".exe");
        Path invalid = Files.createTempFile("curseforge-invalid", ".exe");
        try {
            byte[] header = new byte[68];
            header[0] = 'M';
            header[1] = 'Z';
            header[60] = 64;
            header[64] = 'P';
            header[65] = 'E';
            Files.write(valid, header);
            Files.writeString(invalid, "This is not an executable");

            assertTrue(WrapperInstaller.isWindowsExecutable(valid));
            assertFalse(WrapperInstaller.isWindowsExecutable(invalid));
        } finally {
            Files.deleteIfExists(valid);
            Files.deleteIfExists(invalid);
        }
    }

    @Test
    void downloadsGzipEncodedCurseForgeExecutable() throws Exception {
        Path installer = downloadFromResponse(gzip(windowsExecutable()), "gzip");
        try {
            assertTrue(WrapperInstaller.isWindowsExecutable(installer));
        } finally {
            Files.deleteIfExists(installer);
        }
    }

    @Test
    void downloadsUncompressedCurseForgeExecutable() throws Exception {
        Path installer = downloadFromResponse(windowsExecutable(), null);
        try {
            assertTrue(WrapperInstaller.isWindowsExecutable(installer));
        } finally {
            Files.deleteIfExists(installer);
        }
    }

    @Test
    void rejectsMalformedGzipAndCleansTemporaryDownload() throws Exception {
        IOException failure = assertDownloadFailsAndCleans(new byte[] {1, 2, 3}, "gzip");

        assertFalse(failure.getMessage().contains("not a valid Windows executable"));
    }

    @Test
    void rejectsUnsupportedContentEncodingAndCleansTemporaryDownload() throws Exception {
        IOException failure = assertDownloadFailsAndCleans(windowsExecutable(), "br");

        assertTrue(failure.getMessage().contains("Unsupported HTTP content encoding: br"));
    }

    @Test
    void rejectsInvalidExecutableAndCleansTemporaryDownload() throws Exception {
        IOException failure = assertDownloadFailsAndCleans("not an executable".getBytes(), null);

        assertTrue(failure.getMessage().contains("not a valid Windows executable"));
    }

    private static IOException assertDownloadFailsAndCleans(byte[] body, String contentEncoding)
            throws Exception {
        Set<Path> before = temporaryCurseForgeInstallers();
        IOException failure = assertThrows(IOException.class,
                () -> downloadFromResponse(body, contentEncoding));

        assertEquals(before, temporaryCurseForgeInstallers());
        return failure;
    }

    private static Path downloadFromResponse(byte[] body, String contentEncoding) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/installer.exe", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            if (contentEncoding != null) {
                exchange.getResponseHeaders().set("Content-Encoding", contentEncoding);
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/installer.exe");
            return WrapperInstaller.downloadLauncherInstaller(
                    WrapperInstaller.LauncherApp.CURSEFORGE, url, ignored -> { });
        } finally {
            server.stop(0);
        }
    }

    private static byte[] windowsExecutable() {
        byte[] header = new byte[68];
        header[0] = 'M';
        header[1] = 'Z';
        header[60] = 64;
        header[64] = 'P';
        header[65] = 'E';
        return header;
    }

    private static byte[] gzip(byte[] contents) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(contents);
        }
        return output.toByteArray();
    }

    private static Set<Path> temporaryCurseForgeInstallers() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(file -> file.getFileName().toString().startsWith("automodpack-curseforge-"))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void themesStatusBoxBackground() {
        WrapperInstaller.StatusBox statusBox = new WrapperInstaller.StatusBox();

        statusBox.applyTheme(WrapperInstaller.Theme.DARK);
        assertEquals(new Color(43, 43, 43), statusBox.getBackground());

        statusBox.applyTheme(WrapperInstaller.Theme.LIGHT);
        assertEquals(Color.WHITE, statusBox.getBackground());
    }

    @Test
    void alignsJavaRuntimeAndActivityTextAndExpandsActivityLog() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[] {640, 960}) {
                JPanel javaPanel = WrapperInstaller.createJavaRuntimePanel(new JButton("Install Java"));
                JPanel javaToolbar = (JPanel) javaPanel.getComponent(0);
                JLabel javaLabel = (JLabel) javaToolbar.getComponent(0);
                JButton javaButton = (JButton) javaToolbar.getComponent(2);
                JLabel completion = new JLabel("Ready");
                JPanel activityPanel = WrapperInstaller.createActivityPanel(new JTextArea(), completion,
                        new WrapperInstaller.StatusBox());
                JScrollPane activityLog = (JScrollPane) activityPanel.getComponent(1);

                javaPanel.setSize(width, 100);
                activityPanel.setSize(width, 360);
                javaPanel.doLayout();
                javaToolbar.doLayout();
                activityPanel.doLayout();

                assertEquals(javaToolbar.getX() + javaLabel.getX(), completion.getX());
                assertTrue(javaButton.getX() > javaLabel.getX());
                assertTrue(javaButton.getX() - javaLabel.getX() - javaLabel.getWidth() <= 8,
                        "Java button should sit beside its label rather than at the far edge");
                int initialLogHeight = activityLog.getHeight();

                activityPanel.setSize(width, 560);
                activityPanel.doLayout();
                assertTrue(activityLog.getHeight() > initialLogHeight);
            }
        });
    }

    @Test
    void placesDestinationDirectlyAfterInstallation() {
        JPanel sections = new JPanel(new java.awt.GridBagLayout());
        JPanel installation = new JPanel();
        JPanel destination = new JPanel();
        JPanel javaRuntime = new JPanel();
        JPanel launcherApps = new JPanel();
        JPanel activity = new JPanel();

        WrapperInstaller.addInstallerSections(sections, installation, destination, javaRuntime,
                launcherApps, activity);

        java.awt.GridBagLayout layout = (java.awt.GridBagLayout) sections.getLayout();
        int installationRow = layout.getConstraints(installation).gridy;
        int destinationRow = layout.getConstraints(destination).gridy;
        assertEquals(installationRow + 1, destinationRow);
        assertEquals(destinationRow + 1, layout.getConstraints(javaRuntime).gridy);
        assertEquals(layout.getConstraints(javaRuntime).gridy + 1, layout.getConstraints(launcherApps).gridy);
        assertEquals(layout.getConstraints(launcherApps).gridy + 1, layout.getConstraints(activity).gridy);
    }

    @Test
    void showsDistinctReadableInstallationStates() {
        WrapperInstaller.StatusBox statusBox = new WrapperInstaller.StatusBox();
        javax.swing.JLabel label = (javax.swing.JLabel) statusBox.getComponent(1);

        statusBox.setState(WrapperInstaller.StatusBox.State.IDLE);
        assertEquals("Ready for action", label.getText());
        Component indicator = statusBox.getComponent(0);
        assertEquals("Ready for action", ((javax.swing.JComponent) indicator).getToolTipText());
        indicator.setSize(16, 16);
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        try {
            indicator.paint(graphics);
        } finally {
            graphics.dispose();
        }
        assertEquals(0, icon.getRGB(0, 0) >>> 24, "Idle icon should not paint a gray square background");
        assertTrue(hasBluePixels(icon), "Idle icon should use a distinct blue ready indicator");
        statusBox.setState(WrapperInstaller.StatusBox.State.IN_PROGRESS);
        assertEquals("In progress", label.getText());
        statusBox.setState(WrapperInstaller.StatusBox.State.SUCCESS);
        assertEquals("Success", label.getText());
        statusBox.setState(WrapperInstaller.StatusBox.State.FAILURE);
        assertEquals("Failure", label.getText());
        statusBox.setState(WrapperInstaller.StatusBox.State.UNVERIFIED);
        assertEquals("Not verified", label.getText());
        assertFalse(label.getFont().isBold());
    }

    @Test
    void installationStatusResetsAndNewOperationsCancelTheOldReset() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            WrapperInstaller.StatusBox statusBox = new WrapperInstaller.StatusBox();
            JLabel label = (JLabel) statusBox.getComponent(1);
            JButton java = new JButton("Java");
            JButton launcher = new JButton("Launcher");
            statusBox.setOperationButtons(java, launcher);

            statusBox.beginOperation();
            assertEquals("In progress", label.getText());
            assertFalse(java.isEnabled());
            assertFalse(launcher.isEnabled());
            statusBox.finishOperation(WrapperInstaller.StatusBox.State.SUCCESS);
            assertEquals("Success", label.getText());
            assertTrue(java.isEnabled());
            assertTrue(launcher.isEnabled());

            statusBox.beginOperation();
            statusBox.resetCompletedOperation();
            assertEquals("In progress", label.getText());
            statusBox.finishOperation(WrapperInstaller.StatusBox.State.FAILURE);
            assertEquals("Failure", label.getText());
            statusBox.resetCompletedOperation();
            assertEquals("Ready for action", label.getText());
        });
    }

    @Test
    void completedStatusAutomaticallyReturnsToIdle() throws Exception {
        for (WrapperInstaller.StatusBox.State result : new WrapperInstaller.StatusBox.State[] {
                WrapperInstaller.StatusBox.State.SUCCESS, WrapperInstaller.StatusBox.State.FAILURE,
                WrapperInstaller.StatusBox.State.UNVERIFIED}) {
            CountDownLatch idle = new CountDownLatch(1);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                WrapperInstaller.StatusBox statusBox = new WrapperInstaller.StatusBox(50);
                JLabel label = (JLabel) statusBox.getComponent(1);
                label.addPropertyChangeListener("text", event -> {
                    if ("Ready for action".equals(event.getNewValue())) {
                        idle.countDown();
                    }
                });
                statusBox.beginOperation();
                statusBox.finishOperation(result);
                String expected = switch (result) {
                    case SUCCESS -> "Success";
                    case FAILURE -> "Failure";
                    case UNVERIFIED -> "Not verified";
                    default -> throw new AssertionError("Unexpected completed state: " + result);
                };
                assertEquals(expected, label.getText());
            });
            assertTrue(idle.await(5, TimeUnit.SECONDS), "Status did not return to idle");
        }
    }

    @Test
    void createsFlatControlBordersForBothThemes() {
        Border light = WrapperInstaller.flatControlBorder(WrapperInstaller.Theme.LIGHT);
        Border dark = WrapperInstaller.flatControlBorder(WrapperInstaller.Theme.DARK);

        assertTrue(light instanceof CompoundBorder);
        assertTrue(dark instanceof CompoundBorder);
        assertEquals(new Color(170, 170, 170),
                ((LineBorder) ((CompoundBorder) light).getOutsideBorder()).getLineColor());
        assertEquals(new Color(105, 105, 105),
                ((LineBorder) ((CompoundBorder) dark).getOutsideBorder()).getLineColor());
        assertEquals(4, ((CompoundBorder) light).getInsideBorder().getBorderInsets(null).top);
        assertEquals(8, ((CompoundBorder) light).getInsideBorder().getBorderInsets(null).left);
    }

    @Test
    void enablesLightBlueButtonRollover() {
        JButton button = new JButton("Browse");

        WrapperInstaller.applyComponentTheme(button);

        assertTrue(button.isRolloverEnabled());
        assertEquals(new Color(184, 207, 229), UIManager.getColor("Button.select"));
        assertTrue(button.getBorder() instanceof CompoundBorder);
    }

    @Test
    void givesButtonsRoundedBeveledBorders() {
        JButton button = new JButton("Browse");

        WrapperInstaller.applyComponentTheme(button);

        assertTrue(button.getBorder() instanceof CompoundBorder);
        Border outer = ((CompoundBorder) button.getBorder()).getOutsideBorder();
        assertEquals("RoundedBevelBorder", outer.getClass().getSimpleName());
        assertEquals(2, outer.getBorderInsets(button).top);
        assertEquals(2, outer.getBorderInsets(button).left);
    }

    @Test
    void appliesLightBlueHoverToButtonsAndDropdowns() {
        JButton button = new JButton("Browse");
        JComboBox<String> comboBox = new JComboBox<>(new String[] {"Client", "Server"});

        WrapperInstaller.applyComponentTheme(button);
        WrapperInstaller.applyComponentTheme(comboBox);
        MouseEvent buttonEvent = new MouseEvent(button, MouseEvent.MOUSE_ENTERED,
                System.currentTimeMillis(), 0, 4, 4, 0, false);
        MouseEvent comboEvent = new MouseEvent(comboBox, MouseEvent.MOUSE_ENTERED,
                System.currentTimeMillis(), 0, 4, 4, 0, false);
        for (java.awt.event.MouseListener listener : button.getMouseListeners()) {
            listener.mouseEntered(buttonEvent);
        }
        for (java.awt.event.MouseListener listener : comboBox.getMouseListeners()) {
            listener.mouseEntered(comboEvent);
        }

        assertEquals(new Color(184, 207, 229), button.getClientProperty("WrapperInstaller.hoverTarget"));
        assertTrue(button.getClientProperty("WrapperInstaller.hoverTimer") instanceof javax.swing.Timer);
        assertTrue(comboBox.getClientProperty("WrapperInstaller.comboHoverTimer") instanceof javax.swing.Timer);

        for (java.awt.event.MouseListener listener : button.getMouseListeners()) {
            listener.mouseExited(buttonEvent);
        }
        for (java.awt.event.MouseListener listener : comboBox.getMouseListeners()) {
            listener.mouseExited(comboEvent);
        }

        assertEquals(new Color(238, 238, 238), button.getClientProperty("WrapperInstaller.hoverTarget"));
    }

    @Test
    void interpolatesHoverColorsAcrossTheTransition() {
        assertEquals(new Color(211, 223, 234), WrapperInstaller.interpolateColor(
                new Color(238, 238, 238), new Color(184, 207, 229), 0.5f));
    }

    @Test
    void makesInstallerBackgroundFocusableForDeselectingControls() {
        JPanel background = new JPanel();

        WrapperInstaller.makeBackgroundFocusable(background);

        assertTrue(background.isFocusable());
        assertTrue(background.getMouseListeners().length > 0);
    }

    @Test
    void removesDropdownButtonBorderWhileKeepingOuterBorder() {
        JComboBox<String> comboBox = new JComboBox<>(new String[] {"Custom directory"});

        WrapperInstaller.applyComponentTheme(comboBox);

        assertTrue(comboBox.getBorder() instanceof CompoundBorder);
        assertTrue(((CompoundBorder) comboBox.getBorder()).getOutsideBorder() instanceof LineBorder);
        assertEquals("FlatComboBoxUI", comboBox.getUI().getClass().getSimpleName());
        for (java.awt.Component child : comboBox.getComponents()) {
            if (child instanceof javax.swing.AbstractButton button) {
                assertEquals(0, button.getBorder().getBorderInsets(button).top);
                assertEquals(0, button.getBorder().getBorderInsets(button).left);
            }
        }
    }

    @Test
    void keepsComboLabelsVisibleAndFillsSelectedCell() {
        JComboBox<String> comboBox = new JComboBox<>(new String[] {"Custom directory", "Launcher profile"});
        comboBox.setPrototypeDisplayValue("Custom directory");

        WrapperInstaller.applyComponentTheme(comboBox);

        assertTrue(comboBox.getPreferredSize().width > 100);
        JList<String> list = new JList<>();
        Component selected = comboBox.getRenderer().getListCellRendererComponent(
                list, "Launcher profile", 0, true, false);
        assertTrue(selected.isOpaque());
        assertEquals(list.getSelectionBackground(), selected.getBackground());
        assertEquals(list.getSelectionForeground(), selected.getForeground());
    }

    @Test
    void leavesCurrentComboValueRendererTransparentForFullWidthBackground() {
        JComboBox<String> comboBox = new JComboBox<>(new String[] {"Client", "Server"});
        WrapperInstaller.applyComponentTheme(comboBox);

        Component currentValue = comboBox.getRenderer().getListCellRendererComponent(
                new JList<>(), "Server", -1, false, false);

        assertFalse(currentValue.isOpaque());
    }

    @Test
    void fillsEntireFocusedComboValueWithSelectionColor() {
        JComboBox<String> comboBox = new JComboBox<>(new String[] {"Client", "Server"});
        WrapperInstaller.applyComponentTheme(comboBox);
        BasicComboBoxUI comboBoxUI = (BasicComboBoxUI) comboBox.getUI();
        comboBox.setSize(160, 24);
        BufferedImage image = new BufferedImage(160, 24, BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = image.createGraphics();
        try {
            comboBoxUI.paintCurrentValueBackground(graphics, new Rectangle(0, 0, 100, 24), true);
        } finally {
            graphics.dispose();
        }

		assertEquals(UIManager.getColor("ComboBox.selectionBackground"), new Color(image.getRGB(150, 12)));
		assertEquals(UIManager.getColor("ComboBox.selectionBackground"), new Color(image.getRGB(0, 12)));
    }

    private static boolean hasBluePixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() > 0 && color.getBlue() > color.getRed() + 25
                        && color.getBlue() > color.getGreen()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<JButton> buttonsIn(Container container) {
        List<JButton> buttons = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button) {
                buttons.add(button);
            } else if (component instanceof Container child) {
                buttons.addAll(buttonsIn(child));
            }
        }
        return buttons;
    }
}
