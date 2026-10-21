package net.minecraftforge.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

final class OfficialInstallerLauncher {
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

    static int run(File installer, String action, File target) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(javaExecutable(), "-jar", installer.getAbsolutePath(), action, target.getAbsolutePath())
                .inheritIO()
                .start();
        return process.waitFor();
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return new File(System.getProperty("java.home"), "bin" + File.separator + executable).getAbsolutePath();
    }
}