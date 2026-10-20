package net.minecraftforge.installer.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilExternalResourceTest {
    @Test
    void prefersSelectedInstallerResource() throws IOException {
        Util.setExternalResources(new byte[0], Collections.singletonMap("data/client.lzma", "selected".getBytes(StandardCharsets.UTF_8)));

        try (java.io.InputStream stream = Util.getResourceAsStream("/data/client.lzma")) {
            assertEquals("selected", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}