package net.minecraftforge.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.io.File;

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
    void providesDirectLauncherInstallerEndpoints() {
        assertEquals("https://www.curseforge.com/download/app?clientType=standalone",
                WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.CURSEFORGE));
        assertTrue(WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.MODRINTH)
                .startsWith("https://api.github.com/repos/modrinth/theseus/releases/latest"));
        assertFalse(WrapperInstaller.launcherAppUrl(WrapperInstaller.LauncherApp.CURSEFORGE)
                .contains("overwolf"));
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

        WrapperInstaller.applyComponentTheme(button, WrapperInstaller.Theme.LIGHT);

        assertTrue(button.isRolloverEnabled());
        assertEquals(new Color(184, 207, 229), UIManager.getColor("Button.select"));
        assertTrue(button.getBorder() instanceof CompoundBorder);
    }

    @Test
    void givesButtonsRoundedBeveledBorders() {
        JButton button = new JButton("Browse");

        WrapperInstaller.applyComponentTheme(button, WrapperInstaller.Theme.LIGHT);

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

        WrapperInstaller.applyComponentTheme(button, WrapperInstaller.Theme.LIGHT);
        WrapperInstaller.applyComponentTheme(comboBox, WrapperInstaller.Theme.LIGHT);
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

        WrapperInstaller.applyComponentTheme(comboBox, WrapperInstaller.Theme.LIGHT);

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

        WrapperInstaller.applyComponentTheme(comboBox, WrapperInstaller.Theme.LIGHT);

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
        WrapperInstaller.applyComponentTheme(comboBox, WrapperInstaller.Theme.LIGHT);

        Component currentValue = comboBox.getRenderer().getListCellRendererComponent(
                new JList<>(), "Server", -1, false, false);

        assertFalse(currentValue.isOpaque());
    }

    @Test
    void fillsEntireFocusedComboValueWithSelectionColor() {
        JComboBox<String> comboBox = new JComboBox<>(new String[] {"Client", "Server"});
        WrapperInstaller.applyComponentTheme(comboBox, WrapperInstaller.Theme.LIGHT);
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
