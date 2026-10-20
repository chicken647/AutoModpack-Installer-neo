package net.minecraftforge.installer.automodpack;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Installation {
    private Installation() {
    }

    public static void installAutomodpack(String gameVersion, Path minecraftDirectory) {
        try {
            String downloadUrl = ModrinthApi.getLatestDownloadUrl(gameVersion);
            if (downloadUrl == null) {
                System.out.println("AutoModpack is not available for Minecraft " + gameVersion);
                return;
            }

            Path modsDirectory = minecraftDirectory.resolve("mods");
            Files.createDirectories(modsDirectory);
            String path = new URL(downloadUrl).getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            System.out.println("Downloading AutoModpack " + fileName);
            try (InputStream input = new URL(downloadUrl).openStream()) {
                Files.copy(input, modsDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.out.println("Failed to download AutoModpack: " + e.getMessage());
        }
    }

    public static void installAutomodpack(String gameVersion, Path minecraftDirectory, Object monitor) {
        installAutomodpack(gameVersion, minecraftDirectory);
    }
}