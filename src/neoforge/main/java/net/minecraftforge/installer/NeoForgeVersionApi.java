package net.minecraftforge.installer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NeoForgeVersionApi {
    private static final String METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String INSTALLER_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s-installer.jar";

    private NeoForgeVersionApi() {
    }

    static List<String> getVersions() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(METADATA_URL).openConnection();
        connection.setRequestProperty("User-Agent", "AutoModpack-NeoForge-Installer/1.0");
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            String xml = readAll(reader);
            return parseVersions(xml);
        } finally {
            connection.disconnect();
        }
    }

    static List<String> parseVersions(String xml) {
        int start = xml.indexOf("<versions>");
        int end = xml.indexOf("</versions>", start);
        if (start < 0 || end < 0) {
            return Collections.emptyList();
        }
        String values = xml.substring(start, end);
        List<String> result = new ArrayList<>();
        int cursor = 0;
        while ((start = values.indexOf("<version>", cursor)) >= 0) {
            start += "<version>".length();
            end = values.indexOf("</version>", start);
            if (end < 0) {
                break;
            }
            result.add(values.substring(start, end));
            cursor = end + "</version>".length();
        }
        result.sort(Collections.reverseOrder());
        return result;
    }

    static String installerUrl(String version) {
        return String.format(INSTALLER_URL, version, version);
    }

    private static String readAll(InputStreamReader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }
}