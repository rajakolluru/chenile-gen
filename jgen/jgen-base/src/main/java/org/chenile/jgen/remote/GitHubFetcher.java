package org.chenile.jgen.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads releases through the GitHub REST API. Set {@code GITHUB_TOKEN} (or {@code GH_TOKEN}) to raise the API rate
 * limit. Phase 1 downloads public release assets only.
 */
public class GitHubFetcher implements BlueprintFetcher {
    private static final String API = "https://api.github.com/repos/";
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String token = firstNonBlank(System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"));

    @Override
    public String latestVersion(BlueprintReference reference) throws IOException {
        JsonNode release = getJson(API + reference.repository() + "/releases/latest", reference);
        return release.path("tag_name").asText(null);
    }

    @Override
    public List<Asset> assets(BlueprintReference reference) throws IOException {
        JsonNode release = getJson(API + reference.repository() + "/releases/tags/" + reference.version(), reference);
        List<Asset> assets = new ArrayList<>();
        for (JsonNode asset : release.path("assets"))
            assets.add(new Asset(asset.path("name").asText(), asset.path("browser_download_url").asText()));
        return assets;
    }

    @Override
    public void download(Asset asset, Path target) throws IOException {
        // No token here: release downloads redirect to a storage host that rejects foreign credentials.
        HttpRequest request = request(asset.url(), false).header("Accept", "application/octet-stream").build();
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> response = send(request, HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(partial);
            throw new IOException("Download of " + asset.url() + " failed with HTTP " + response.statusCode());
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private JsonNode getJson(String url, BlueprintReference reference) throws IOException {
        HttpResponse<String> response = send(request(url, true).header("Accept", "application/vnd.github+json").build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404)
            throw new IOException("No such release: " + reference + " (" + url + ")");
        if (response.statusCode() != 200)
            throw new IOException("GitHub returned HTTP " + response.statusCode() + " for " + url
                    + (response.statusCode() == 403 ? "; set GITHUB_TOKEN if you hit the rate limit" : ""));
        return mapper.readTree(response.body());
    }

    private HttpRequest.Builder request(String url, boolean authenticate) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "jgen");
        if (authenticate && token != null) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return http.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + request.uri(), e);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b != null && !b.isBlank() ? b : null;
    }
}
