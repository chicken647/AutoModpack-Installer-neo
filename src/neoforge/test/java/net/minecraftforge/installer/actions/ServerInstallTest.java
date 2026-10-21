package net.minecraftforge.installer.actions;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerInstallTest {
    @Test
    void updatesLauncherToSelectedNeoForgeVersion() throws Exception {
        Path directory = Files.createTempDirectory("neoforge-server-test");
        Path runBat = directory.resolve("run.bat");
        Path args = directory.resolve("libraries/net/neoforged/neoforge/26.3.0.95/win_args.txt");
        Files.createDirectories(args.getParent());
        Files.writeString(runBat,
                "java @libraries/net/neoforged/neoforge/26.1.2.76/win_args.txt %*\r\n",
                StandardCharsets.UTF_8);
        Files.writeString(args,
                "-classpath libraries/net/neoforged/neoforge/26.1.2.76/neoforge-26.1.2.76-universal.jar\n"
                        + "net.neoforged.fml.startup.Server\n"
                        + "--fml.neoForgeVersion 26.1.2.76\n"
                        + "--fml.mcVersion 26.1.2\n",
                StandardCharsets.UTF_8);

        ServerInstall.updateLauncherScripts(directory.toFile(), "neoforge-26.3.0.95", "26.3");

        assertTrue(Files.readString(runBat, StandardCharsets.UTF_8)
                .contains("libraries/net/neoforged/neoforge/26.3.0.95/win_args.txt"));
        String updatedArgs = Files.readString(args, StandardCharsets.UTF_8);
        assertTrue(updatedArgs.contains("net/neoforged/neoforge/26.3.0.95/"));
        assertTrue(updatedArgs.contains("--fml.neoForgeVersion 26.3.0.95"));
        assertTrue(updatedArgs.contains("--fml.mcVersion 26.3"));
    }

    @Test
    void validatesServerLoaderMainClass() throws Exception {
        Path loader = Files.createTempFile("neoforge-loader", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(loader))) {
            output.putNextEntry(new JarEntry("net/neoforged/fml/startup/Server.class"));
            output.write(0);
            output.closeEntry();
        }

        assertTrue(ServerInstall.containsServerMainClass(loader.toFile()));
    }

    @Test
    void acceptsLoaderDeclaredByInstallerProfileLibraries() throws Exception {
        Path libraries = Files.createTempDirectory("neoforge-profile-libraries");
        Path loader = libraries.resolve("net/neoforged/fancymodloader/loader/1.0/loader-1.0.jar");
        Files.createDirectories(loader.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(loader))) {
            output.putNextEntry(new JarEntry("net/neoforged/fml/startup/Server.class"));
            output.write(0);
            output.closeEntry();
        }

        net.minecraftforge.installer.json.Version.Library library = new net.minecraftforge.installer.json.Version.Library();
        java.lang.reflect.Field name = net.minecraftforge.installer.json.Version.Library.class.getDeclaredField("name");
        name.setAccessible(true);
        name.set(library, net.minecraftforge.installer.json.Artifact.from("net.neoforged.fancymodloader:loader:1.0"));

        assertTrue(ServerInstall.isServerLoaderLibrary(library));
    }
}