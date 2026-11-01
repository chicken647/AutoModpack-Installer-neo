package net.minecraftforge.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.Color;

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
    void namesAppearanceThemes() {
        assertEquals("Light", WrapperInstaller.themeName(WrapperInstaller.Theme.LIGHT));
        assertEquals("Dark", WrapperInstaller.themeName(WrapperInstaller.Theme.DARK));
    }

    @Test
    void themesStatusBoxBackground() {
        WrapperInstaller.StatusBox statusBox = new WrapperInstaller.StatusBox();

        statusBox.applyTheme(WrapperInstaller.Theme.DARK);
        assertEquals(new Color(43, 43, 43), statusBox.getBackground());

        statusBox.applyTheme(WrapperInstaller.Theme.LIGHT);
        assertEquals(Color.WHITE, statusBox.getBackground());
    }
}
