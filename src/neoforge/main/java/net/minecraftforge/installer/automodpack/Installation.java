package net.minecraftforge.installer.automodpack;

import net.minecraftforge.installer.actions.ProgressCallback;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Installation {
    private Installation() {
    }

    public static void installAutomodpack(String gameVersion, Path minecraftDirectory, ProgressCallback monitor) {
        try {
            String downloadUrl = ModrinthApi.getLatestDownloadUrl(gameVersion);
            if (downloadUrl == null) {
                monitor.message("AutoModpack is not available for Minecraft " + gameVersion);
                return;
            }

            Path modsDirectory = minecraftDirectory.resolve("mods");
            Files.createDirectories(modsDirectory);
            String path = new URL(downloadUrl).getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            monitor.stage("Downloading AutoModpack " + fileName, true);
            try (InputStream input = new URL(downloadUrl).openStream()) {
                Files.copy(input, modsDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            monitor.message("Failed to download AutoModpack: " + e.getMessage());
        }
    }
}