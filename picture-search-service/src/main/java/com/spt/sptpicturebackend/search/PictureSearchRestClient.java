package com.spt.sptpicturebackend.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Small typed boundary around the Elasticsearch 7.17 low-level REST client. */
@Slf4j
@Component
public class PictureSearchRestClient {

    @Resource(name = "pictureSearchHttpClient")
    private RestClient restClient;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PictureSearchProperties properties;

    public JsonNode requestJson(String method, String endpoint, Object body) throws IOException {
        Request request = new Request(method, endpoint);
        if (body != null) {
            request.setJsonEntity(objectMapper.writeValueAsString(body));
        }
        Response response = restClient.performRequest(request);
        if (response.getEntity() == null) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(response.getEntity().getContent());
    }

    public JsonNode requestRawJson(String method, String endpoint, String body, String contentType)
            throws IOException {
        Request request = new Request(method, endpoint);
        request.setEntity(new org.apache.http.entity.StringEntity(body,
                ContentType.create(contentType, StandardCharsets.UTF_8)));
        Response response = restClient.performRequest(request);
        if (response.getEntity() == null) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(response.getEntity().getContent());
    }

    public boolean exists(String index) throws IOException {
        try {
            Response response = restClient.performRequest(new Request("HEAD", "/" + encode(index)));
            return response.getStatusLine().getStatusCode() < 400;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public boolean index(String target, String id, Map<String, Object> document, long version) throws IOException {
        String endpoint = "/" + encode(target) + "/_doc/" + encode(id)
                + "?version=" + version + "&version_type=external&refresh=wait_for";
        try {
            requestJson("PUT", endpoint, document);
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 409) {
                return false;
            }
            throw e;
        }
    }

    public void delete(String target, String id, long version) throws IOException {
        String endpoint = "/" + encode(target) + "/_doc/" + encode(id)
                + "?version=" + version + "&version_type=external&refresh=wait_for";
        try {
            requestJson("DELETE", endpoint, null);
        } catch (ResponseException e) {
            int status = e.getResponse().getStatusLine().getStatusCode();
            if (status != 404 && status != 409) {
                throw e;
            }
        }
    }

    public JsonNode bulkIndex(String target, List<PictureSearchBulkItem> items) throws IOException {
        StringBuilder body = new StringBuilder();
        for (PictureSearchBulkItem item : items) {
            body.append(objectMapper.writeValueAsString(Map.of(
                            "index", Map.of("_index", target, "_id", String.valueOf(item.id()),
                                    "version", item.version(), "version_type", "external"))))
                    .append('\n');
            body.append(objectMapper.writeValueAsString(item.document())).append('\n');
        }
        return requestRawJson("POST", "/" + encode(target) + "/_bulk", body.toString(),
                "application/x-ndjson");
    }

    public boolean hasNonVersionFailure(JsonNode bulkResponse) {
        if (bulkResponse == null || !bulkResponse.path("errors").asBoolean(false)) {
            return false;
        }
        Iterator<JsonNode> values = bulkResponse.elements();
        while (values.hasNext()) {
            JsonNode group = values.next();
            if (!group.isArray()) continue;
            for (JsonNode item : group) {
                JsonNode result = item.elements().hasNext() ? item.elements().next() : null;
                if (result != null && result.path("status").asInt(200) != 409) {
                    return true;
                }
            }
        }
        return false;
    }

    public JsonNode bulkDelete(String target, List<PictureSearchBulkItem> items) throws IOException {
        StringBuilder body = new StringBuilder();
        for (PictureSearchBulkItem item : items) {
            body.append(objectMapper.writeValueAsString(Map.of(
                            "delete", Map.of("_index", target, "_id", String.valueOf(item.id()),
                                    "version", item.version(), "version_type", "external"))))
                    .append('\n');
        }
        return requestRawJson("POST", "/" + encode(target) + "/_bulk", body.toString(),
                "application/x-ndjson");
    }

    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            requestJson("GET", "/_cluster/health", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record PictureSearchBulkItem(Long id, long version, Map<String, Object> document) {
    }
}
