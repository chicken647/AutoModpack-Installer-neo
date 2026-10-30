package net.minecraftforge.installer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OfficialInstallerLauncherTest {
    @Test
    void rejectsUntrustedVersionBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> OfficialInstallerLauncher.download("1.21.1/../../internal-resource"));
    }
}
