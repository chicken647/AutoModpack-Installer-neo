package net.minecraftforge.installer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}