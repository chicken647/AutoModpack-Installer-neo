package net.minecraftforge.installer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class LauncherProfileLocator {
    enum Type {
        CURSEFORGE("CurseForge"),
        MODRINTH("Modrinth");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    private LauncherProfileLocator() {
    }

    static List<Path> findProfiles(Type type) {
        List<Path> profiles = new ArrayList<>();
        for (Path root : profileRoots(type, Path.of(System.getProperty("user.home")), System.getProperty("os.name", ""))) {
            profiles.addAll(findProfiles(root));
        }
        return profiles;
    }

    static List<Path> findProfiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory).sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).toList();
        } catch (java.io.IOException ignored) {
            return List.of();
        }
    }

    static List<Path> profileRoots(Type type, Path home, String osName) {
        String os = osName.toLowerCase();
        if (os.contains("win")) {
            Path appData = home.resolve("AppData").resolve("Roaming");
            return type == Type.CURSEFORGE
                    ? List.of(home.resolve("curseforge").resolve("minecraft").resolve("Instances"),
                    appData.resolve("CurseForge").resolve("minecraft").resolve("Instances"))
                    : List.of(appData.resolve("ModrinthApp").resolve("profiles"));
        }
        if (os.contains("mac")) {
            Path appData = home.resolve("Library").resolve("Application Support");
            return type == Type.CURSEFORGE
                    ? List.of(home.resolve("Documents").resolve("curseforge").resolve("minecraft").resolve("Instances"),
                    appData.resolve("curseforge").resolve("minecraft").resolve("Instances"))
                    : List.of(appData.resolve("ModrinthApp").resolve("profiles"));
        }
        return type == Type.CURSEFORGE
                ? List.of(home.resolve("Documents").resolve("curseforge").resolve("minecraft").resolve("Instances"),
                home.resolve(".curseforge").resolve("minecraft").resolve("Instances"))
                : List.of(home.resolve(".local").resolve("share").resolve("ModrinthApp").resolve("profiles"));
    }

    static String displayName(Type type, Path profile) {
        return type.displayName() + ": " + profile.getFileName();
    }
}