package mod.hey.studios.project.github;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import pro.sketchware.utility.Network;

public class GitHubApiClient {
    private static final String API_BASE = "https://api.github.com";
    private final Network network;
    private final String token;

    public GitHubApiClient(@Nullable String token) {
        this.network = new Network();
        this.token = token;
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.github.v3+json");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "token " + token);
        }
        return headers;
    }

    public void validateToken(Network.ResponseHandler handler) {
        network.get(API_BASE + "/user", getHeaders(), handler);
    }

    public void getUser(Network.ResponseHandler handler) {
        network.get(API_BASE + "/user", getHeaders(), handler);
    }

    public void createRepo(String name, String description, boolean isPrivate, Network.ResponseHandler handler) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("private", isPrivate);
        body.put("auto_init", true);

        String json = new Gson().toJson(body);
        network.post(API_BASE + "/user/repos", getHeaders(), json, handler);
    }

    public void getRepo(String owner, String repo, Network.ResponseHandler handler) {
        network.get(API_BASE + "/repos/" + owner + "/" + repo, getHeaders(), handler);
    }

    public void uploadFile(String owner, String repo, String path, String base64Content,
                           @Nullable String sha, String commitMessage, Network.ResponseHandler handler) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", commitMessage);
        body.put("content", base64Content);
        if (sha != null) {
            body.put("sha", sha);
        }

        String json = new Gson().toJson(body);
        network.put(API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + path, getHeaders(), json, handler);
    }

    public void getFileInfo(String owner, String repo, String path, Network.ResponseHandler handler) {
        network.get(API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + path, getHeaders(), handler);
    }

    public void listRepoContents(String owner, String repo, @Nullable String path, Network.ResponseHandler handler) {
        String url = API_BASE + "/repos/" + owner + "/" + repo + "/contents";
        if (path != null && !path.isEmpty()) {
            url += "/" + path;
        }
        network.get(url, getHeaders(), handler);
    }

    /**
     * Synchronous binary download. Must be called from a background thread.
     */
    public byte[] downloadFileBytes(String url) throws IOException {
        return network.downloadBytes(url, getHeaders());
    }

    /**
     * Parse a GitHub URL to extract owner and repo name.
     * Supports: https://github.com/owner/repo, github.com/owner/repo, .git suffix, trailing slash
     *
     * @return String array [owner, repo] or null if invalid
     */
    public static String[] parseGitHubUrl(String url) {
        if (url == null || url.isEmpty()) return null;

        url = url.trim();

        // Remove protocol
        url = url.replaceFirst("^https?://", "");

        // Remove www.
        url = url.replaceFirst("^www\\.", "");

        // Must start with github.com
        if (!url.startsWith("github.com/")) return null;

        // Remove github.com/
        url = url.substring("github.com/".length());

        // Remove trailing slash
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        // Remove .git suffix
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);

        // Split into parts
        String[] parts = url.split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;

        return new String[]{parts[0], parts[1]};
    }
}
