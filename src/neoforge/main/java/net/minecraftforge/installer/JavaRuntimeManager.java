package net.minecraftforge.installer;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class JavaRuntimeManager {
    private static final String JABBA_INSTALLER =
            "https://github.com/Jabba-Team/jabba/raw/main/install.ps1";
    private static final String JDK_VENDOR = "microsoft";

    private JavaRuntimeManager() {
    }

    static int recommendedMajor() {
        return 25;
    }

    static String vendor() {
        return JDK_VENDOR;
    }

    static String jabbaVersion(int major) {
        return JDK_VENDOR + "@" + major;
    }

    static Path jabbaHome() {
        String configured = System.getenv("JABBA_HOME");
        return Path.of(configured == null || configured.isBlank()
                ? System.getProperty("user.home") + "/.jabba" : configured).toAbsolutePath().normalize();
    }

    static Path executable(Path javaHome) {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return javaHome.resolve("bin").resolve(executable);
    }

    static Path ensureJdk(int major, Consumer<String> output) throws IOException {
        Path jabba = findJabba();
        if (jabba == null) {
            installJabba(output);
            jabba = findJabba();
            if (jabba == null) {
                throw new IOException("Jabba was installed, but its executable could not be found. Restart the installer and try again.");
            }
        }
        String version = jabbaVersion(major);
        output.accept("Installing " + version + " with Jabba...");
        String installedVersion = installJdk(jabba, version, output);
        Path java = resolveJava(jabba, installedVersion, output);
        addToUserPath(java.getParent(), output);
        output.accept("Installed system Java " + major + " at " + java);
        return java.getParent().getParent();
    }

    private static String installJdk(Path jabba, String version, Consumer<String> output) throws IOException {
        try {
            run(List.of(jabba.toString(), "install", version), output);
            return version;
        } catch (IOException exception) {
            if (!isWindows() || !isSymlinkPrivilegeFailure(exception.getMessage())) {
                throw exception;
            }
            output.accept("Jabba needs administrator permission to create its Java version alias. "
                    + "Please approve the Windows prompt.");
            runElevatedInstall(jabba, version, output);
            return version;
        }
    }

    static boolean isSymlinkPrivilegeFailure(String message) {
        return message != null && message.toLowerCase().contains("required privilege is not held by the client");
    }

    static String elevatedInstallScript(Path jabba, String version) {
        return "$p = Start-Process -FilePath '" + powershellQuote(jabba.toString())
                + "' -ArgumentList @('install', '" + powershellQuote(version)
                + "') -Verb RunAs -Wait -PassThru; if ($p.ExitCode -ne 0) { exit $p.ExitCode }";
    }

    private static void runElevatedInstall(Path jabba, String version, Consumer<String> output) throws IOException {
        run(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                elevatedInstallScript(jabba, version)), output);
    }

    private static String powershellQuote(String value) {
        return value.replace("'", "''");
    }

    static Path resolveJava(Path jabba, String version, Consumer<String> output) throws IOException {
        IOException whichFailure = null;
        try {
            ProcessResult result = run(List.of(jabba.toString(), "which", version), output);
            String stdout = result.stdout.strip();
            if (!stdout.isEmpty()) {
                String[] lines = stdout.split("\\R");
                Path java = Path.of(lines[lines.length - 1].trim());
                if (Files.isRegularFile(java)) {
                    return java.toAbsolutePath().normalize();
                }
            }
        } catch (IOException exception) {
            whichFailure = exception;
        }

        Path java = findInstalledJava(jabbaHome().resolve("jdk"), version);
        if (java != null) {
            output.accept("Located installed Java at " + java);
            return java;
        }
        if (whichFailure != null && !isSymlinkPrivilegeFailure(whichFailure.getMessage())) {
            throw whichFailure;
        }
        throw new IOException("Jabba installed Java, but its executable could not be located.");
    }

    static Path findInstalledJava(Path jdkDirectory, String requestedVersion) throws IOException {
        Path exact = executable(jdkDirectory.resolve(requestedVersion));
        if (Files.isRegularFile(exact)) {
            return exact.toAbsolutePath().normalize();
        }
        if (!Files.isDirectory(jdkDirectory)) {
            return null;
        }
        try (Stream<Path> entries = Files.list(jdkDirectory)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(requestedVersion + "."))
                    .map(JavaRuntimeManager::executable)
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .map(path -> path.toAbsolutePath().normalize())
                    .orElse(null);
        }
    }

    static Path findJabba() {
        List<Path> candidates = new ArrayList<>();
        String configured = System.getenv("JABBA_HOME");
        if (configured != null && !configured.isBlank()) {
            candidates.add(Path.of(configured).resolve("bin"));
        }
        candidates.add(jabbaHome().resolve("bin"));
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "jabba.exe" : "jabba";
        for (Path directory : candidates) {
            Path candidate = directory.resolve(executable);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void installJabba(Consumer<String> output) throws IOException {
        output.accept("Jabba was not found. Installing Jabba...");
        if (isWindows()) {
            run(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                    "Invoke-Expression (Invoke-WebRequest -Uri '" + JABBA_INSTALLER
                            + "' -UseBasicParsing).Content"), output);
        } else {
            run(List.of("sh", "-c", "curl -fsSL 'https://github.com/Jabba-Team/jabba/raw/main/install.sh' | bash"), output);
        }
    }

    private static void addToUserPath(Path bin, Consumer<String> output) throws IOException {
        if (isWindows()) {
            run(List.of("setx", "PATH", System.getenv().getOrDefault("PATH", "") + ";" + bin), output);
            output.accept("Added Jabba Java to the user PATH. Restart applications to use the updated PATH.");
        } else {
            output.accept("Jabba manages this Java installation and can add it to PATH in your shell profile.");
        }
    }

    private static ProcessResult run(List<String> command, Consumer<String> output) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line).append(System.lineSeparator());
                if (output != null) {
                    output.accept(line);
                }
            }
        }
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Command failed: " + command.get(0) + "\n" + stdout);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Java installation was interrupted", exception);
        }
        return new ProcessResult(stdout.toString());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record ProcessResult(String stdout) {
    }
}