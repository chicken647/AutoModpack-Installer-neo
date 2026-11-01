package net.minecraftforge.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JavaRuntimeManagerTest {
    @Test
    void usesTheScriptRecommendedJavaVersion() {
        assertEquals(25, JavaRuntimeManager.recommendedMajor());
    }

    @Test
    void usesTheScriptRecommendedVendor() {
        assertEquals("microsoft", JavaRuntimeManager.vendor());
    }

    @Test
    void buildsJabbaVersionIdentifier() {
        assertEquals("microsoft@25", JavaRuntimeManager.jabbaVersion(25));
    }

    @Test
    void placesJavaExecutableUnderJabbaJavaHome() {
        assertTrue(JavaRuntimeManager.executable(Path.of("runtime")).toString().endsWith(
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "bin\\java.exe" : "bin/java"));
    }

    @Test
    void detectsWindowsSymlinkPrivilegeFailure() {
        assertTrue(JavaRuntimeManager.isSymlinkPrivilegeFailure(
                "symlink C:\\Users\\user\\.jabba\\jdk\\microsoft@25.0.3 "
                        + "C:\\Users\\user\\.jabba\\jdk\\microsoft@25.0: "
                        + "A required privilege is not held by the client."));
    }

    @Test
    void buildsAdministratorInstallRequest() {
        String script = JavaRuntimeManager.elevatedInstallScript(
                Path.of("C:\\Users\\user\\.jabba\\bin\\jabba.exe"), "microsoft@25");
        assertTrue(script.contains("-Verb RunAs"));
        assertTrue(script.contains("-Wait"));
        assertTrue(script.contains("microsoft@25"));
    }

    @Test
    void findsConcreteJdkWhenJabbaAliasIsUnavailable() throws Exception {
        Path jdkDirectory = Files.createTempDirectory("jabba-jdk");
        Path java = jdkDirectory.resolve("microsoft@25.0.3").resolve("bin")
                .resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe" : "java");
        Files.createDirectories(java.getParent());
        Files.createFile(java);

        assertEquals(java.toAbsolutePath().normalize(),
                JavaRuntimeManager.findInstalledJava(jdkDirectory, "microsoft@25"));
    }
}