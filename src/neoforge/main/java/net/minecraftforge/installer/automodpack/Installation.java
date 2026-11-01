package net.minecraftforge.installer.automodpack;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public final class Installation {
    private Installation() {}

    public static void installAutomodpack(String gameVersion, Path minecraftDirectory) {
        installAutomodpack(gameVersion, minecraftDirectory, System.out::println);
    }

    public static void installAutomodpack(String gameVersion, Path minecraftDirectory, Consumer<String> output) {
        installAutomodpack(gameVersion, "neoforge", minecraftDirectory, output);
    }

    public static void installAutomodpack(String gameVersion, String loader, Path minecraftDirectory,
            Consumer<String> output) {
        try {
            installAutomodpackOrThrow(gameVersion, loader, minecraftDirectory, output);
        } catch (IOException e) {
            output.accept("Failed to install AutoModpack: " + e.getMessage());
        }
    }

    public static void installAutomodpackOrThrow(String gameVersion, String loader, Path minecraftDirectory,
            Consumer<String> output) throws IOException {
        String downloadUrl = ModrinthApi.getLatestDownloadUrl(loader, gameVersion);
        if (downloadUrl == null) {
            throw new IOException("AutoModpack is not available for " + loader
                    + " on Minecraft " + gameVersion);
        }

        Path modsDirectory = minecraftDirectory.resolve("mods");
        Files.createDirectories(modsDirectory);
        String path = new URL(downloadUrl).getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        output.accept("Downloading AutoModpack " + fileName);
        try (InputStream input = new URL(downloadUrl).openStream()) {
            Files.copy(input, modsDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
        output.accept("AutoModpack installed: " + modsDirectory.resolve(fileName));
    }

    public static void installAutomodpack(String gameVersion, Path minecraftDirectory, Object monitor) {
        installAutomodpack(gameVersion, minecraftDirectory);
    }
}
