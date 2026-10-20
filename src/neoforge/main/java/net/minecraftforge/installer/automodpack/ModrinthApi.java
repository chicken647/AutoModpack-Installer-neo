package net.minecraftforge.installer.automodpack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

final class ModrinthApi {
    private static final String PROJECT_ID = "k68glP2e";
    private static final String USER_AGENT = "AutoModpack-NeoForge-Installer/1.0";

    private ModrinthApi() {
    }

    static String getLatestDownloadUrl(String gameVersion) throws IOException {
        String endpoint = buildApiUrl(gameVersion);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestMethod("GET");

        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), "UTF-8")) {
            JsonArray versions = new JsonParser().parse(reader).getAsJsonArray();
            if (versions.size() == 0) {
                return null;
            }
            JsonObject file = versions.get(0).getAsJsonObject().getAsJsonArray("files").get(0).getAsJsonObject();
            return file.get("url").getAsString();
        } finally {
            connection.disconnect();
        }
    }

    static String buildApiUrl(String gameVersion) {
        return "https://api.modrinth.com/v2/project/" + PROJECT_ID
                + "/version?loaders=%5B%22neoforge%22%5D&game_versions=%5B%22"
                + gameVersion.replace("\"", "") + "%22%5D";
    }
}