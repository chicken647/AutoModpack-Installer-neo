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
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;

class WrapperInstallerTest {
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
    void showsDistinctReadableInstallationStates() {
        WrapperInstaller.StatusBox statusBox = new WrapperInstaller.StatusBox();
        javax.swing.JLabel label = (javax.swing.JLabel) statusBox.getComponent(1);

        statusBox.setState(WrapperInstaller.StatusBox.State.IDLE);
        assertEquals("Idle", label.getText());
        statusBox.setState(WrapperInstaller.StatusBox.State.IN_PROGRESS);
        assertEquals("In progress", label.getText());
        statusBox.setState(WrapperInstaller.StatusBox.State.SUCCESS);
        assertEquals("Success", label.getText());
        statusBox.setState(WrapperInstaller.StatusBox.State.FAILURE);
        assertEquals("Failure", label.getText());
        assertFalse(label.getFont().isBold());
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
}
