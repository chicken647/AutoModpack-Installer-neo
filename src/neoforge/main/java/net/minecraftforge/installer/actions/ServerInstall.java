/*
 * Installer
 * Copyright (c) 2016-2018.
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.minecraftforge.installer.actions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.json.Version.Download;
import net.minecraftforge.installer.ui.TranslatedMessage;

public class ServerInstall extends Action {
    public static boolean serverStarterJar;

    private final List<Artifact> grabbed = new ArrayList<>();

    public ServerInstall(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, false);
    }

    @Override
    public boolean run(File target, Predicate<String> optionals, File installer) throws ActionCanceledException {
        if (target.exists() && !target.isDirectory()) {
            error("There is a file at this location, the server cannot be installed here!");
            return false;
        }

        File librariesDir = new File(target, "libraries");
        monitor.message("Server install target: " + target.getAbsolutePath());
        monitor.message("Server libraries directory: " + librariesDir.getAbsolutePath());
        if (!target.exists())
            target.mkdirs();
        librariesDir.mkdir();
        if (profile.getMirror() != null && profile.getMirror().isAdvertised())
            monitor.stage(getSponsorMessage());
        checkCancel();

        //Download MC Server jar
        monitor.stage("Considering Minecraft server jar", true);
        Map<String, String> tokens = new HashMap<>();
        tokens.put("ROOT", target.getAbsolutePath());
        tokens.put("MINECRAFT_VERSION", profile.getMinecraft());
        tokens.put("LIBRARY_DIR", librariesDir.getAbsolutePath());

        String path = Util.replaceTokens(tokens, profile.getServerJarPath());
        File serverTarget = new File(path);
        monitor.message("Minecraft server jar: " + serverTarget.getAbsolutePath());
        if (!serverTarget.exists()) {
            File parent = serverTarget.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }

            File versionJson = new File(target, profile.getMinecraft() + ".json");
            Version vanilla = Util.getVanillaVersion(monitor, profile.getMinecraft(), versionJson);
            if (vanilla == null) {
                error("Failed to download version manifest, can not find server jar URL.");
                return false;
            }
            Download server = vanilla.getDownload("server");
            if (server == null) {
                error("Failed to download minecraft server, info missing from manifest: " + versionJson);
                return false;
            }

            versionJson.delete();

            if (!monitor.downloader(server.getUrl())
                    .sha(server.getSha1())
                    .localPath("minecraft/" + profile.getMinecraft() + "/server.jar")
                    .download(serverTarget)) {
                serverTarget.delete();
                error("Downloading minecraft server failed, invalid checksum.\n" +
                        "Try again, or manually place server jar to skip download.");
                return false;
            }
        }
        checkCancel();

        // Download Libraries
        List<File> libDirs = new ArrayList<>();
        File mcLibDir = new File(SimpleInstaller.getMCDir(), "libraries");
        if (mcLibDir.exists()) {
            libDirs.add(mcLibDir);
        }
        if (!downloadLibraries(librariesDir, optionals, libDirs))
            return false;

        if (!ensureServerLoader(librariesDir, libDirs))
            return false;

        checkCancel();
        if (!processors.process(librariesDir, serverTarget, target, installer))
            return false;
        monitor.message("Server processors completed");

        try {
            updateLauncherScripts(target, profile.getVersion(), profile.getMinecraft(), monitor);
        } catch (IOException e) {
            error("Failed to update server launcher scripts: " + e.getMessage());
            return false;
        }

        if (serverStarterJar) {
            monitor.downloader(DownloadUtils.SERVER_STARTER_JAR)
                    .localPath("serverstarter.jar")
                    .download(new File(target, "server.jar"));
        }

        return true;
    }

    private boolean ensureServerLoader(File librariesDir, List<File> additionalLibDirs) {
        List<Version.Library> libraries = new ArrayList<>();
        libraries.addAll(Arrays.asList(version.getLibraries()));
        libraries.addAll(Arrays.asList(processors.getLibraries()));
        return ensureServerLoader(libraries, librariesDir, additionalLibDirs);
    }

    private boolean ensureServerLoader(List<Version.Library> libraries, File librariesDir, List<File> additionalLibDirs) {
        for (Version.Library library : libraries) {
            Artifact artifact = library.getName();
            if (!isServerLoaderLibrary(library))
                continue;

            File loader = artifact.getLocalPath(librariesDir);
            if (!loader.isFile()) {
                monitor.message("Ensuring NeoForge server loader is installed: " + artifact.getDescriptor());
                if (!DownloadUtils.downloadLibrary(monitor, library, librariesDir, value -> true, grabbed, additionalLibDirs)) {
                    error("Failed to install the NeoForge server loader: " + artifact.getDescriptor());
                    return false;
                }
            }
            if (!containsServerMainClass(loader)) {
                error("The NeoForge server loader is invalid or incomplete: " + loader.getAbsolutePath());
                return false;
            }
            return true;
        }
        error("The selected NeoForge version does not provide a server loader.");
        return false;
    }

    static boolean isServerLoaderLibrary(Version.Library library) {
        Artifact artifact = library.getName();
        return artifact != null && artifact.getDescriptor().startsWith("net.neoforged.fancymodloader:loader:");
    }

    static boolean containsServerMainClass(File loader) {
        if (!loader.isFile())
            return false;
        try (JarFile jar = new JarFile(loader)) {
            return jar.getEntry("net/neoforged/fml/startup/Server.class") != null;
        } catch (IOException e) {
            return false;
        }
    }

    static void updateLauncherScripts(File target, String profileVersion) throws IOException {
        updateLauncherScripts(target, profileVersion, null, null);
    }

    private static void updateLauncherScripts(File target, String profileVersion, ProgressCallback monitor) throws IOException {
        updateLauncherScripts(target, profileVersion, null, monitor);
    }

    static void updateLauncherScripts(File target, String profileVersion, String minecraftVersion) throws IOException {
        updateLauncherScripts(target, profileVersion, minecraftVersion, null);
    }

    private static void updateLauncherScripts(File target, String profileVersion, String minecraftVersion, ProgressCallback monitor) throws IOException {
        if (profileVersion == null || !profileVersion.startsWith("neoforge-")) {
            return;
        }
        String version = profileVersion.substring("neoforge-".length());
        for (String name : new String[]{"run.bat", "run.sh"}) {
            File script = new File(target, name);
            if (!script.isFile()) {
                if (monitor != null)
                    monitor.message("Launcher script not found: " + script.getAbsolutePath());
                continue;
            }
            String contents = Files.readString(script.toPath(), StandardCharsets.UTF_8);
            String updated = contents.replaceAll(
                    "(libraries/net/neoforged/neoforge/)[^/\\r\\n]+(/(?:win_args|unix_args)\\.txt)",
                    "$1" + version + "$2");
            if (!contents.equals(updated)) {
                Files.writeString(script.toPath(), updated, StandardCharsets.UTF_8);
                if (monitor != null)
                    monitor.message("Updated launcher script for NeoForge " + version + ": " + script.getAbsolutePath());
            } else {
                if (monitor != null)
                    monitor.message("Launcher script already references NeoForge " + version + ": " + script.getAbsolutePath());
            }
        }

        File libraries = new File(target, "libraries/net/neoforged/neoforge/" + version);
        for (String name : new String[]{"win_args.txt", "unix_args.txt"}) {
            File args = new File(libraries, name);
            if (!args.isFile()) {
                continue;
            }
            String contents = Files.readString(args.toPath(), StandardCharsets.UTF_8);
            String updated = contents.replaceAll(
                    "(?m)(--fml\\.neoForgeVersion\\s+)\\S+", "$1" + version);
            if (minecraftVersion != null) {
                updated = updated.replaceAll(
                        "(?m)(--fml\\.mcVersion\\s+)\\S+", "$1" + minecraftVersion);
            }
            updated = updated.replaceAll(
                    "(net/neoforged/neoforge/)[^/\\\\\\s]+(/)", "$1" + version + "$2");
            if (!contents.equals(updated)) {
                Files.writeString(args.toPath(), updated, StandardCharsets.UTF_8);
                if (monitor != null)
                    monitor.message("Updated NeoForge launcher arguments: " + args.getAbsolutePath());
            }
        }
    }

    @Override
    public TargetValidator getTargetValidator() {
        return TargetValidator.shouldExist(false)
                .and(TargetValidator.isDirectory())
                .and(TargetValidator.shouldBeEmpty());
    }

    @Override
    public TranslatedMessage getSuccessMessage() {
        if (grabbed.isEmpty()) {
            return new TranslatedMessage("installer.action.install.server.finished.withoutlibs", profile.getVersion());
        }
        return new TranslatedMessage("installer.action.install.server.finished.withlibs", grabbed.size(), profile.getVersion());
    }
}
