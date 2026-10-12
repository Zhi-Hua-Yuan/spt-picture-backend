package com.spt.sptpicturebackend.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import com.spt.sptpicturebackend.search.v1.SearchContractsV1;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Cluster-internal read API. Search never receives a user session or a Core DB connection. */
@RestController
@Slf4j
@RequestMapping("/internal/v1/search")
public class InternalSearchController {
    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final Set<String> SORT_FIELDS = Set.of("_score", "createTime", "editTime", "picSize");

    @Resource private PictureSearchRestClient restClient;
    @Resource private PictureSearchProperties properties;
    @Resource private ObjectMapper objectMapper;

    @PostMapping("/pictures")
    public SearchContractsV1.SearchQueryResponse pictures(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestBody SearchContractsV1.SearchQueryRequest request) {
        authorize(token);
        if (request == null) throw badRequest("request is required");
        long current = Math.max(1, request.current());
        long pageSize = Math.min(Math.max(1, request.pageSize()), properties.getMaxPageSize());
        long from = (current - 1) * pageSize;
        if (from + pageSize > properties.getMaxResultWindow()) throw badRequest("page range is too large");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("query", query(request));
            body.put("from", from);
            body.put("size", pageSize);
            body.set("sort", sort(request));
            ObjectNode highlight = body.putObject("highlight");
            highlight.putArray("pre_tags").add("__PICTURE_HL_START__");
            highlight.putArray("post_tags").add("__PICTURE_HL_END__");
            highlight.putObject("fields").putObject("name");
            highlight.putObject("fields").putObject("introduction");
            JsonNode response = restClient.requestJson("POST", "/" + PictureSearchIndexService.READ_ALIAS + "/_search", body);
            List<SearchContractsV1.SearchHit> hits = new ArrayList<>();
            for (JsonNode hit : response.path("hits").path("hits")) {
                long id = hit.path("_id").asLong(hit.path("_source").path("id").asLong(0));
                if (id <= 0) continue;
                hits.add(new SearchContractsV1.SearchHit(id,
                        highlight(hit, "name"), highlight(hit, "introduction")));
            }
            return new SearchContractsV1.SearchQueryResponse(hits,
                    response.path("hits").path("total").path("value").asLong(0), current, pageSize);
        } catch (Exception error) {
            log.warn("Search pictures query failed, endpoint={}, message={}", PictureSearchIndexService.READ_ALIAS,
                    error.getMessage(), error);
            throw serviceUnavailable("search backend unavailable", error);
        }
    }

    @GetMapping("/suggestions")
    public List<String> suggestions(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestParam String keyword,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(defaultValue = "8") Integer limit) {
        authorize(token);
        if (keyword == null || keyword.isBlank()) return List.of();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("query", queryForKeyword(keyword, spaceId));
            body.put("size", Math.min(Math.max(limit == null ? 8 : limit, 1), 10));
            body.set("_source", objectMapper.createArrayNode().add("name"));
            JsonNode response = restClient.requestJson("POST", "/" + PictureSearchIndexService.READ_ALIAS + "/_search", body);
            List<String> result = new ArrayList<>();
            for (JsonNode hit : response.path("hits").path("hits")) {
                String name = hit.path("_source").path("name").asText("");
                if (!name.isBlank() && !result.contains(name)) result.add(name);
            }
            return result;
        } catch (Exception error) {
            log.warn("Search suggestions query failed, endpoint={}, message={}", PictureSearchIndexService.READ_ALIAS,
                    error.getMessage(), error);
            throw serviceUnavailable("suggestions backend unavailable", error);
        }
    }

    @GetMapping("/facets")
    public SearchContractsV1.SearchFacetResponse facets(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestParam(required = false) Long spaceId) {
        authorize(token);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("query", queryForKeyword(null, spaceId));
            body.put("size", 0);
            body.putObject("aggs").putObject("categories").putObject("terms").put("field", "category").put("size", 100);
            body.with("aggs").putObject("tags").putObject("terms").put("field", "tags").put("size", 100);
            JsonNode response = restClient.requestJson("POST", "/" + PictureSearchIndexService.READ_ALIAS + "/_search", body);
            return new SearchContractsV1.SearchFacetResponse(
                    buckets(response.path("aggregations").path("categories").path("buckets")),
                    buckets(response.path("aggregations").path("tags").path("buckets")));
        } catch (Exception error) {
            log.warn("Search facets query failed, endpoint={}, message={}", PictureSearchIndexService.READ_ALIAS,
                    error.getMessage(), error);
            throw serviceUnavailable("facets backend unavailable", error);
        }
    }

    private ObjectNode query(SearchContractsV1.SearchQueryRequest request) {
        ObjectNode bool = baseScope(request.scope(), request.spaceId());
        ArrayNode filter = (ArrayNode) bool.path("bool").path("filter");
        if (request.category() != null && !request.category().isBlank()) filter.addObject().putObject("term").put("category", request.category());
        if (request.picFormat() != null && !request.picFormat().isBlank()) filter.addObject().putObject("term").put("picFormat", request.picFormat());
        range(filter, "picSize", request.minPicSize(), request.maxPicSize());
        range(filter, "picWidth", request.minPicWidth(), request.maxPicWidth());
        range(filter, "picHeight", request.minPicHeight(), request.maxPicHeight());
        range(filter, "editTime", request.startEditTime(), request.endEditTime());
        if (request.keyword() != null && !request.keyword().isBlank()) {
            bool.with("bool").putArray("must").addObject().putObject("multi_match")
                    .put("query", request.keyword()).putArray("fields").add("name^5").add("tags.text^3")
                    .add("category.text^2").add("introduction^2").add("picFormat.text");
        } else {
            bool.with("bool").putArray("must").addObject().putObject("match_all");
        }
        return bool;
    }

    private ObjectNode queryForKeyword(String keyword, Long spaceId) {
        SearchContractsV1.SearchQueryRequest request = new SearchContractsV1.SearchQueryRequest(
                keyword, null, null, spaceId, spaceId == null ? "PUBLIC" : "SPACE", null, null,
                null, null, null, null, null, null, null, null, null, 1, 10, "_score", "desc");
        return query(request);
    }

    private ObjectNode baseScope(String scope, Long spaceId) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode bool = root.putObject("bool");
        ArrayNode filters = bool.putArray("filter");
        filters.addObject().putObject("term").put("processStatus", 2);
        filters.addObject().putObject("term").put("isDelete", 0);
        if ("SPACE".equalsIgnoreCase(scope) && spaceId != null) {
            filters.addObject().putObject("term").put("spaceId", spaceId);
        } else {
            filters.addObject().putObject("bool").putArray("must_not").addObject().putObject("exists").put("field", "spaceId");
            filters.addObject().putObject("term").put("reviewStatus", 1);
        }
        return root;
    }

    private ArrayNode sort(SearchContractsV1.SearchQueryRequest request) {
        String field = request.sortField() == null || request.sortField().isBlank()
                ? (request.keyword() == null || request.keyword().isBlank() ? "createTime" : "_score")
                : request.sortField();
        if (!SORT_FIELDS.contains(field)) throw badRequest("unsupported sort field");
        ArrayNode result = objectMapper.createArrayNode();
        result.addObject().putObject(field).put("order", "ascend".equalsIgnoreCase(request.sortOrder()) ? "asc" : "desc");
        if ("_score".equals(field)) result.addObject().putObject("editTime").put("order", "desc");
        result.addObject().putObject("id").put("order", "desc");
        return result;
    }

    private void range(ArrayNode filter, String field, Object gte, Object lt) {
        if (gte == null && lt == null) return;
        ObjectNode node = filter.addObject().putObject("range").putObject(field);
        if (gte != null) node.set("gte", objectMapper.valueToTree(gte));
        if (lt != null) node.set("lt", objectMapper.valueToTree(lt));
    }

    private String highlight(JsonNode hit, String field) {
        JsonNode values = hit.path("highlight").path(field);
        return values.isArray() && values.size() > 0 ? values.get(0).asText() : null;
    }

    private List<SearchContractsV1.FacetItem> buckets(JsonNode buckets) {
        List<SearchContractsV1.FacetItem> result = new ArrayList<>();
        for (JsonNode bucket : buckets) result.add(new SearchContractsV1.FacetItem(bucket.path("key").asText(), bucket.path("doc_count").asLong()));
        return result;
    }

    private void authorize(String token) {
        byte[] expected = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException serviceUnavailable(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
