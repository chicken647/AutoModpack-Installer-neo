package net.minecraftforge.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OfficialInstallerLauncherTest {
    @Test
    void addsJavaVersionCheckToServerLauncher() throws Exception {
        Path directory = Files.createTempDirectory("neoforge-launcher-test");
        Path launcher = directory.resolve("run.bat");
        Files.writeString(launcher, "@echo off\r\njava @libraries\\args.txt %*\r\n", StandardCharsets.UTF_8);

        OfficialInstallerLauncher.prepareServerLauncher(directory.toFile(), "26.3.0.95");

        String updated = Files.readString(launcher, StandardCharsets.UTF_8);
        assertTrue(updated.startsWith("REM AutoModpack NeoForge Java version check\r\n"));
        assertTrue(updated.contains("NeoForge requires Java 25 or newer"));
    }

    @Test
    void doesNotAddJavaVersionCheckToOlderNeoForgeLauncher() throws Exception {
        Path directory = Files.createTempDirectory("neoforge-launcher-test");
        Path launcher = directory.resolve("run.bat");
        Files.writeString(launcher, "@echo off\r\njava @libraries\\args.txt %*\r\n", StandardCharsets.UTF_8);

        OfficialInstallerLauncher.prepareServerLauncher(directory.toFile(), "26.1.2.76");

        assertTrue(Files.readString(launcher, StandardCharsets.UTF_8).startsWith("@echo off"));
    }

    @Test
    void doesNotAddDuplicateJavaVersionCheck() throws Exception {
        Path directory = Files.createTempDirectory("neoforge-launcher-test");
        Path launcher = directory.resolve("run.bat");
        Files.writeString(launcher, "REM AutoModpack NeoForge Java version check\r\n", StandardCharsets.UTF_8);

        OfficialInstallerLauncher.prepareServerLauncher(directory.toFile(), "26.3.0.95");

        assertEquals("REM AutoModpack NeoForge Java version check\r\n",
                Files.readString(launcher, StandardCharsets.UTF_8));
    }

}