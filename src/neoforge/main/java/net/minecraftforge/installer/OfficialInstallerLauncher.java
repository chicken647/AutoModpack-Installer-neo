package net.minecraftforge.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class OfficialInstallerLauncher {
    private static final int REQUIRED_MINECRAFT_MAJOR = 26;
    private static final int REQUIRED_MINECRAFT_MINOR = 3;

    private OfficialInstallerLauncher() {
    }

    static File download(String version) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(NeoForgeVersionApi.installerUrl(version)).openConnection();
        connection.setRequestProperty("User-Agent", "AutoModpack-NeoForge-Installer/1.0");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(120_000);
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("NeoForge installer download returned HTTP " + connection.getResponseCode());
        }
        File result = File.createTempFile("neoforge-" + version + "-", "-installer.jar");
        result.deleteOnExit();
        try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(result.toPath())) {
            input.transferTo(output);
        } finally {
            connection.disconnect();
        }
        return result;
    }

    static int run(File installer, String action, String version, File target) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(javaExecutable(), "-jar", installer.getAbsolutePath(), action, target.getAbsolutePath())
                .inheritIO()
                .start();
        int exitCode = process.waitFor();
        if (exitCode == 0 && "--installServer".equals(action)) {
            prepareServerLauncher(target, version);
        }
        return exitCode;
    }

    static boolean requiresConfiguredJavaVersion(String version) {
        if (version == null) {
            return false;
        }
        String[] parts = version.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int minecraftMajor = Integer.parseInt(parts[0]);
            int minecraftMinor = Integer.parseInt(parts[1]);
            return minecraftMajor > REQUIRED_MINECRAFT_MAJOR
                    || minecraftMajor == REQUIRED_MINECRAFT_MAJOR && minecraftMinor >= REQUIRED_MINECRAFT_MINOR;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    static int javaMajorVersion() {
        String version = System.getProperty("java.version", "");
        String[] parts = version.split("\\.");
        try {
            int first = Integer.parseInt(parts[0]);
            return first == 1 && parts.length > 1 ? Integer.parseInt(parts[1]) : first;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    static void prepareServerLauncher(File target, String version) throws IOException {
        if (!requiresConfiguredJavaVersion(version)) {
            return;
        }
        File launcher = new File(target, "run.bat");
        if (!launcher.isFile()) {
            return;
        }
        String contents = Files.readString(launcher.toPath(), StandardCharsets.UTF_8);
        String marker = "REM AutoModpack NeoForge Java version check";
        if (contents.contains(marker)) {
            return;
        }
        String check = marker + "\r\n"
                + "for /f \"tokens=3\" %%v in ('java -version 2^>^&1 ^| findstr /i \"version\"') do for /f \"tokens=1 delims=.\" %%m in (\"%%~v\") do set \"NEOFORGE_JAVA_MAJOR=%%m\"\r\n"
                + "if not defined NEOFORGE_JAVA_MAJOR goto neoforge_java_error\r\n"
                + "if %NEOFORGE_JAVA_MAJOR% LSS 25 goto neoforge_java_error\r\n"
                + "goto neoforge_java_ok\r\n"
                + ":neoforge_java_error\r\n"
                + "echo NeoForge requires Java 25 or newer for this installation.\r\n"
                + "echo Install Java 25 or newer and ensure it is first on PATH, then run this file again.\r\n"
                + "java -version\r\n"
                + "pause\r\n"
                + "exit /b 1\r\n"
                + ":neoforge_java_ok\r\n";
        Files.writeString(launcher.toPath(), check + contents, StandardCharsets.UTF_8);
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return new File(System.getProperty("java.home"), "bin" + File.separator + executable).getAbsolutePath();
    }
}