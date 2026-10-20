package net.minecraftforge.installer.automodpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthApiTest {
    @Test
    void requestsNeoForgeVersion() {
        String url = ModrinthApi.buildApiUrl("1.20.1\"&unexpected=true");

        assertTrue(url.contains("loaders=%5B%22neoforge%22%5D"));
        assertTrue(url.contains("game_versions=%5B%221.20.1&unexpected=true%22%5D"));
    }
}