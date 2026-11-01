package net.minecraftforge.installer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

final class NeoForgeVersionApi {
    private static final String METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String INSTALLER_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s-installer.jar";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?");

    private NeoForgeVersionApi() {}

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
        result.sort(Comparator.comparing(NeoForgeVersionApi::versionParts,
                NeoForgeVersionApi::compareVersionParts).reversed());
        return result;
    }

    private static List<String> versionParts(String version) {
        List<String> parts = new ArrayList<>();
        for (String part : version.split("[.-]")) {
            parts.add(part);
        }
        return parts;
    }

    private static int compareVersionParts(List<String> left, List<String> right) {
        int count = Math.max(left.size(), right.size());
        for (int index = 0; index < count; index++) {
            String leftPart = index < left.size() ? left.get(index) : "";
            String rightPart = index < right.size() ? right.get(index) : "";
            boolean leftNumeric = leftPart.chars().allMatch(Character::isDigit);
            boolean rightNumeric = rightPart.chars().allMatch(Character::isDigit);
            int comparison;
            if (leftNumeric && rightNumeric) {
                comparison = compareNumericParts(leftPart, rightPart);
            } else if (leftNumeric != rightNumeric) {
                comparison = leftNumeric ? 1 : -1;
            } else {
                comparison = leftPart.compareToIgnoreCase(rightPart);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int compareNumericParts(String left, String right) {
        String normalizedLeft = left.replaceFirst("^0+(?!$)", "");
        String normalizedRight = right.replaceFirst("^0+(?!$)", "");
        int lengthComparison = Integer.compare(normalizedLeft.length(), normalizedRight.length());
        return lengthComparison != 0 ? lengthComparison : normalizedLeft.compareTo(normalizedRight);
    }

    static String installerUrl(String version) {
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("Invalid NeoForge version");
        }
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
