package net.minecraftforge.installer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class LauncherProfileLocator {
    record ProfileInfo(String gameVersion, String loader) {}
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

    private LauncherProfileLocator() {}

    static List<Path> findProfiles(Type type) {
        List<Path> profiles = new ArrayList<>();
        for (Path root : existingProfileRoots(type, Path.of(System.getProperty("user.home")), System.getProperty("os.name", ""))) {
            profiles.addAll(findProfiles(root));
        }
        return profiles;
    }

    static List<Path> existingProfileRoots(Type type, Path home, String osName) {
        return profileRoots(type, home, osName).stream()
                .filter(Files::isDirectory)
                .toList();
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

    static ProfileInfo readProfile(Path profile) {
        for (String fileName : List.of("profile.json", "minecraftinstance.json")) {
            Path metadata = profile.resolve(fileName);
            if (!Files.isRegularFile(metadata)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(metadata)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String gameVersion = firstString(json, "game_version", "gameVersion", "minecraft_version",
                        "minecraftVersion");
                String loader = firstString(json, "loader", "mod_loader", "modLoader", "modLoaderType",
                        "baseModLoader");
                if (loader != null && loader.contains("-")) {
                    loader = loader.substring(0, loader.indexOf('-'));
                }
                if (gameVersion != null && loader != null) {
                    return new ProfileInfo(gameVersion, loader.toLowerCase());
                }
            } catch (IOException | RuntimeException ignored) {
                // Try the other supported launcher metadata format.
            }
        }
        return null;
    }

    private static String firstString(JsonObject json, String... names) {
        for (String name : names) {
            if (json.has(name) && json.get(name).isJsonPrimitive()) {
                return json.get(name).getAsString();
            }
        }
        return null;
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
