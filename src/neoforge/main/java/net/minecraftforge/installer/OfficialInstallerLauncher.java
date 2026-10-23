package net.minecraftforge.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OfficialInstallerLauncher {
    private static final String OFFICIAL_HOST = "maven.neoforged.net";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?");

    private OfficialInstallerLauncher() {
    }

    static File download(String version) throws IOException {
        String safeVersion = sanitizeVersion(version);
        URL installerUrl = new URL(NeoForgeVersionApi.installerUrl(safeVersion));
        if (!"https".equalsIgnoreCase(installerUrl.getProtocol()) || !OFFICIAL_HOST.equalsIgnoreCase(installerUrl.getHost())) {
            throw new IOException("NeoForge installer URL is not an official HTTPS endpoint");
        }
        HttpURLConnection connection = (HttpURLConnection) installerUrl.openConnection();
        connection.setRequestProperty("User-Agent", "AutoModpack-NeoForge-Installer/1.0");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(120_000);
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("NeoForge installer download returned HTTP " + connection.getResponseCode());
        }
        File result = File.createTempFile("neoforge-" + safeVersion + "-", "-installer.jar");
        result.deleteOnExit();
        try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(result.toPath())) {
            input.transferTo(output);
        } finally {
            connection.disconnect();
        }
        return result;
    }

    private static String sanitizeVersion(String version) {
        if (version == null) {
            throw new IllegalArgumentException("NeoForge version must not be null");
        }
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid NeoForge version");
        }
        return matcher.group();
    }

    static int run(File installer, String action, File target) throws IOException, InterruptedException {
        return run(installer, action, target, null);
    }

    static int run(File installer, String action, File target, Consumer<String> output) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(javaExecutable(), "-jar", installer.getAbsolutePath(), action, target.getAbsolutePath());
        if (output == null) {
            builder.inheritIO();
        } else {
            builder.redirectErrorStream(true);
        }
        Process process = builder.start();
        if (output != null) {
            try (java.io.BufferedReader reader = process.inputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.accept(line);
                }
            }
        }
        return process.waitFor();
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return new File(System.getProperty("java.home"), "bin" + File.separator + executable).getAbsolutePath();
    }
}