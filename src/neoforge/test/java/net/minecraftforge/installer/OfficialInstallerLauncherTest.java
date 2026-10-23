package net.minecraftforge.installer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficialInstallerLauncherTest {
    @Test
    void rejectsUntrustedVersionBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> OfficialInstallerLauncher.download("1.21.1/../../internal-resource"));
    }
}