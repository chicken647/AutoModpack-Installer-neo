package net.minecraftforge.installer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherProfileLocatorTest {
    @Test
    void findsOnlyDirectoriesInProfileRoot() throws Exception {
        Path root = Files.createTempDirectory("profiles");
        Files.createDirectory(root.resolve("Create"));
        Files.createDirectory(root.resolve("Vanilla"));
        Files.writeString(root.resolve("not-a-profile"), "file");

        assertEquals(List.of(root.resolve("Create"), root.resolve("Vanilla")),
                LauncherProfileLocator.findProfiles(root));
    }

    @Test
    void findsProfilesInCustomLocation() throws Exception {
        Path root = Files.createTempDirectory("custom-profiles");
        Files.createDirectory(root.resolve("Custom profile"));

        assertEquals(List.of(root.resolve("Custom profile")), LauncherProfileLocator.findProfiles(root));
    }

    @Test
    void usesKnownLauncherProfileLocations() {
        Path home = Path.of("home");

        assertTrue(LauncherProfileLocator.profileRoots(LauncherProfileLocator.Type.CURSEFORGE, home, "Windows 11")
                .contains(home.resolve("curseforge/minecraft/Instances")));
        assertEquals(List.of(home.resolve("AppData/Roaming/ModrinthApp/profiles")),
                LauncherProfileLocator.profileRoots(LauncherProfileLocator.Type.MODRINTH, home, "Windows 11"));
    }
}