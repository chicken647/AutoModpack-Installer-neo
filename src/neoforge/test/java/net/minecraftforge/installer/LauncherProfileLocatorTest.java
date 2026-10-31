package net.minecraftforge.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

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

    @Test
    void filtersDetectedProfileRootsToExistingDirectories() throws Exception {
        Path home = Files.createTempDirectory("launcher-home");
        Path modrinthRoot = home.resolve("AppData/Roaming/ModrinthApp/profiles");
        Files.createDirectories(modrinthRoot);

        assertEquals(List.of(modrinthRoot), LauncherProfileLocator.existingProfileRoots(
                LauncherProfileLocator.Type.MODRINTH, home, "Windows 11"));
    }

    @Test
    void readsModrinthProfileMetadata() throws Exception {
        Path profile = Files.createTempDirectory("modrinth-profile");
        Files.writeString(profile.resolve("profile.json"), ""
                + "{\"game_version\":\"1.20.1\",\"loader\":\"fabric\"}");

        assertEquals(new LauncherProfileLocator.ProfileInfo("1.20.1", "fabric"),
                LauncherProfileLocator.readProfile(profile));
    }

    @Test
    void readsCurseForgeProfileMetadata() throws Exception {
        Path profile = Files.createTempDirectory("curseforge-profile");
        Files.writeString(profile.resolve("minecraftinstance.json"), ""
                + "{\"gameVersion\":\"1.21.1\",\"modLoader\":\"neoforge-21.1.80\"}");

        assertEquals(new LauncherProfileLocator.ProfileInfo("1.21.1", "neoforge"),
                LauncherProfileLocator.readProfile(profile));
    }

    @Test
    void rejectsProfileWithoutLoaderMetadata() throws Exception {
        Path profile = Files.createTempDirectory("incomplete-profile");
        Files.writeString(profile.resolve("profile.json"), "{\"game_version\":\"1.20.1\"}");

        assertNull(LauncherProfileLocator.readProfile(profile));
    }
}
