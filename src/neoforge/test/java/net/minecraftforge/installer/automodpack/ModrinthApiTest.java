package net.minecraftforge.installer.automodpack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModrinthApiTest {
    @Test
    void requestsNeoForgeVersion() {
        String url = ModrinthApi.buildApiUrl("neoforge", "1.20.1\"&unexpected=true");

        assertTrue(url.contains("loaders=%5B%22neoforge%22%5D"));
        assertTrue(url.contains("game_versions=%5B%221.20.1%22%26unexpected%3Dtrue%22%5D"));
    }

    @Test
    void requestsFabricVersion() {
        assertTrue(ModrinthApi.buildApiUrl("fabric", "1.20.1")
                .contains("loaders=%5B%22fabric%22%5D"));
    }
}
