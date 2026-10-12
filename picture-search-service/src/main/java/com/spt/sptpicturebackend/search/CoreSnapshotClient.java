package com.spt.sptpicturebackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import com.spt.sptpicturebackend.search.v1.SearchContractsV1;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Search-side client for Core's authoritative snapshot, never for a database. */
@Component
public class CoreSnapshotClient {
    @Resource private ObjectMapper objectMapper;
    @Resource private PictureSearchProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    public SearchContractsV1.SnapshotPage page(Long afterId, int limit) throws IOException {
        String suffix = "/internal/v1/search/snapshots?limit=" + limit + (afterId == null ? "" : "&afterId=" + afterId);
        return get(suffix, SearchContractsV1.SnapshotPage.class);
    }

    public SearchContractsV1.Snapshot snapshot(Long pictureId) throws IOException {
        SearchContractsV1.SnapshotPage page = get("/internal/v1/search/snapshots?pictureId=" + pictureId, SearchContractsV1.SnapshotPage.class);
        return page.items().isEmpty() ? null : page.items().get(0);
    }

    private <T> T get(String suffix, Class<T> type) throws IOException {
        String base = properties.getCoreBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + suffix))
                .timeout(Duration.ofMillis(1500))
                .header("X-Internal-Token", properties.getInternalToken())
                .GET().build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("core snapshot request interrupted", interrupted);
        }
        if (response.statusCode() >= 400) throw new IOException("core snapshot response: " + response.statusCode());
        return objectMapper.readValue(response.body(), type);
    }
}
