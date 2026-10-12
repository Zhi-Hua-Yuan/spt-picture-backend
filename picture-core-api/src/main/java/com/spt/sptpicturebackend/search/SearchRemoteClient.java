package com.spt.sptpicturebackend.search;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import com.spt.sptpicturebackend.search.v1.SearchContractsV1;
import com.spt.sptpicturebackend.model.dto.picture.PictureSearchRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

/** HTTP boundary used by Core after Search becomes database-autonomous. */
@Component
public class SearchRemoteClient {
    @Resource private ObjectMapper objectMapper;
    @Resource private PictureSearchProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();

    @CircuitBreaker(name = "pictureSearchRemote")
    @Retry(name = "pictureSearchRemote")
    public SearchContractsV1.SearchQueryResponse search(PictureSearchRequest request, Long spaceId) throws IOException {
        SearchContractsV1.SearchQueryRequest payload = new SearchContractsV1.SearchQueryRequest(
                request.getKeyword(), request.getName(), request.getIntroduction(), spaceId,
                spaceId == null ? "PUBLIC" : "SPACE", request.getCategory(), request.getTags(), request.getPicFormat(),
                request.getMinPicSize(), request.getMaxPicSize(), request.getMinPicWidth(), request.getMaxPicWidth(),
                request.getMinPicHeight(), request.getMaxPicHeight(), toInstant(request.getStartEditTime()),
                toInstant(request.getEndEditTime()), request.getCurrent(), request.getPageSize(),
                request.getSortField(), request.getSortOrder());
        return post("/internal/v1/search/pictures", payload, SearchContractsV1.SearchQueryResponse.class);
    }

    @CircuitBreaker(name = "pictureSearchRemote")
    @Retry(name = "pictureSearchRemote")
    public List<String> suggestions(String keyword, Long spaceId, int limit) throws IOException {
        String path = "/internal/v1/search/suggestions?keyword=" + encode(keyword)
                + (spaceId == null ? "" : "&spaceId=" + spaceId) + "&limit=" + limit;
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
        return objectMapper.readValue(get(path), type);
    }

    @CircuitBreaker(name = "pictureSearchRemote")
    @Retry(name = "pictureSearchRemote")
    public SearchContractsV1.SearchFacetResponse facets(Long spaceId) throws IOException {
        String path = "/internal/v1/search/facets" + (spaceId == null ? "" : "?spaceId=" + spaceId);
        return objectMapper.readValue(get(path), SearchContractsV1.SearchFacetResponse.class);
    }

    private <T> T post(String path, Object payload, Class<T> type) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofMillis(1500))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", properties.getInternalToken())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("search request interrupted", interrupted);
        }
        if (response.statusCode() >= 500 || response.statusCode() == 408) {
            throw new IOException("search service unavailable: " + response.statusCode());
        }
        if (response.statusCode() >= 400) {
            throw new IOException("search service rejected request: " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), type);
    }

    private String get(String path) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofMillis(1500))
                .header("X-Internal-Token", properties.getInternalToken())
                .GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("search request interrupted", interrupted);
        }
        if (response.statusCode() >= 400) throw new IOException("search service response: " + response.statusCode());
        return response.body();
    }

    private URI uri(String path) {
        String base = properties.getRemoteBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return URI.create(base + path);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private java.time.Instant toInstant(java.util.Date value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC).toInstant();
    }
}
