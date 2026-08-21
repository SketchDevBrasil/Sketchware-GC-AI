package mod.hey.studios.project.github;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
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
        headers.put("Accept", "application/vnd.github+json");
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    // ===== Async methods (for UI thread usage) =====

    public void validateToken(Network.ResponseHandler handler) {
        network.get(API_BASE + "/user", getHeaders(), handler);
    }

    public void listRepoContents(String owner, String repo, @Nullable String path, Network.ResponseHandler handler) {
        String url = API_BASE + "/repos/" + owner + "/" + repo + "/contents";
        if (path != null && !path.isEmpty()) {
            url += "/" + path;
        }
        network.get(url, getHeaders(), handler);
    }

    // ===== Sync methods (for background thread usage) =====

    public Network.SyncResponse getUserSync() throws IOException {
        return network.requestSync("GET", API_BASE + "/user", getHeaders(), null);
    }

    public Network.SyncResponse createRepoSync(String name, String description, boolean isPrivate) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("private", isPrivate);
        body.put("auto_init", true);

        String json = new Gson().toJson(body);
        return network.requestSync("POST", API_BASE + "/user/repos", getHeaders(), json);
    }

    public Network.SyncResponse getRepoSync(String owner, String repo) throws IOException {
        return network.requestSync("GET", API_BASE + "/repos/" + owner + "/" + repo, getHeaders(), null);
    }

    public Network.SyncResponse uploadFileSync(String owner, String repo, String path, String base64Content,
                                                @Nullable String sha, String commitMessage) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("message", commitMessage);
        body.put("content", base64Content);
        if (sha != null) {
            body.put("sha", sha);
        }

        String json = new Gson().toJson(body);
        return network.requestSync("PUT", API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + path, getHeaders(), json);
    }

    public Network.SyncResponse getFileInfoSync(String owner, String repo, String path) throws IOException {
        return network.requestSync("GET", API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + path, getHeaders(), null);
    }

    public Network.SyncResponse getRefSync(String owner, String repo, String branch) throws IOException {
        return network.requestSync("GET", API_BASE + "/repos/" + owner + "/" + repo + "/git/refs/heads/" + branch, getHeaders(), null);
    }

    public Network.SyncResponse getCommitSync(String owner, String repo, String sha) throws IOException {
        return network.requestSync("GET", API_BASE + "/repos/" + owner + "/" + repo + "/git/commits/" + sha, getHeaders(), null);
    }

    public Network.SyncResponse createBlobSync(String owner, String repo, String base64Content) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("content", base64Content);
        body.put("encoding", "base64");
        return network.requestSync("POST", API_BASE + "/repos/" + owner + "/" + repo + "/git/blobs", getHeaders(), new Gson().toJson(body));
    }

    public Network.SyncResponse createTreeSync(String owner, String repo, @Nullable String baseTreeSha,
                                               List<Map<String, Object>> treeItems) throws IOException {
        Map<String, Object> body = new HashMap<>();
        if (baseTreeSha != null && !baseTreeSha.isEmpty()) {
            body.put("base_tree", baseTreeSha);
        }
        body.put("tree", treeItems);
        return network.requestSync("POST", API_BASE + "/repos/" + owner + "/" + repo + "/git/trees", getHeaders(), new Gson().toJson(body));
    }

    public Network.SyncResponse createCommitSync(String owner, String repo, String message, String treeSha,
                                                 @Nullable String parentCommitSha) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("tree", treeSha);
        if (parentCommitSha != null && !parentCommitSha.isEmpty()) {
            body.put("parents", java.util.Collections.singletonList(parentCommitSha));
        } else {
            body.put("parents", java.util.Collections.emptyList());
        }
        return network.requestSync("POST", API_BASE + "/repos/" + owner + "/" + repo + "/git/commits", getHeaders(), new Gson().toJson(body));
    }

    public Network.SyncResponse createRefSync(String owner, String repo, String branch, String commitSha) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("ref", "refs/heads/" + branch);
        body.put("sha", commitSha);
        return network.requestSync("POST", API_BASE + "/repos/" + owner + "/" + repo + "/git/refs", getHeaders(), new Gson().toJson(body));
    }

    public Network.SyncResponse updateRefSync(String owner, String repo, String branch, String commitSha) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("sha", commitSha);
        return network.requestSync("PATCH", API_BASE + "/repos/" + owner + "/" + repo + "/git/refs/heads/" + branch, getHeaders(), new Gson().toJson(body));
    }

    public Network.SyncResponse listRepoContentsSync(String owner, String repo, @Nullable String path) throws IOException {
        String url = API_BASE + "/repos/" + owner + "/" + repo + "/contents";
        if (path != null && !path.isEmpty()) {
            url += "/" + path;
        }
        return network.requestSync("GET", url, getHeaders(), null);
    }

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
        url = url.replaceFirst("^https?://", "");
        url = url.replaceFirst("^www\\.", "");

        if (!url.startsWith("github.com/")) return null;

        url = url.substring("github.com/".length());

        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);

        String[] parts = url.split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;

        return new String[]{parts[0], parts[1]};
    }
}
