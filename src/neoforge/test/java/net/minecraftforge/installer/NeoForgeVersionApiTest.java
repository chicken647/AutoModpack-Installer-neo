package net.minecraftforge.installer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeVersionApiTest {
    @Test
    void parsesAndSortsVersions() {
        List<String> versions = NeoForgeVersionApi.parseVersions(
                "<metadata><versioning><versions><version>1.20.1-1.0.0</version>"
                        + "<version>1.21.1-2.0.0</version></versions></versioning></metadata>");

        assertEquals(List.of("1.21.1-2.0.0", "1.20.1-1.0.0"), versions);
    }

    @Test
    void handlesMissingVersionBlock() {
        assertTrue(NeoForgeVersionApi.parseVersions("<metadata/>").isEmpty());
    }

    @Test
    void buildsInstallerUrl() {
        assertEquals("https://maven.neoforged.net/releases/net/neoforged/neoforge/1.21.1-2.0.0/neoforge-1.21.1-2.0.0-installer.jar",
                NeoForgeVersionApi.installerUrl("1.21.1-2.0.0"));
    }

    @Test
    void rejectsVersionThatCouldEscapeInstallerPath() {
        assertThrows(IllegalArgumentException.class,
                () -> NeoForgeVersionApi.installerUrl("1.21.1/../../malicious"));
    }
}