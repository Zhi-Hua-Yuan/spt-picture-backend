package com.spt.sptpicturebackend.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.manager.auth.SpaceUserAuthManager;
import com.spt.sptpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.spt.sptpicturebackend.model.dto.picture.PictureSearchRequest;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.Space;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.picture.PictureSearchFacetVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureSearchPageVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureVO;
import com.spt.sptpicturebackend.search.v1.SearchContractsV1;
import com.spt.sptpicturebackend.service.PictureService;
import com.spt.sptpicturebackend.service.SpaceService;
import com.spt.sptpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class PictureSearchService {

    private static final Set<String> SORT_FIELDS = Set.of("_score", "createTime", "editTime", "picSize");
    private static final String READ_ALIAS = "picture_search_read";

    @Resource
    private PictureSearchProperties properties;
    @Resource
    private PictureSearchRestClient restClient;
    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private SearchRemoteClient remoteClient;

    public PictureSearchPageVO search(PictureSearchRequest request, HttpServletRequest servletRequest) {
        validate(request);
        SearchScope scope = authorize(request.getSpaceId(), servletRequest);
        String mode = properties.getMode() == null ? "MYSQL" : properties.getMode().toUpperCase();
        if (!properties.isEnabled() || "MYSQL".equals(mode)) {
            return mysqlSearch(request, scope, servletRequest);
        }
        if ("REMOTE".equals(mode)) {
            try {
                return remoteSearch(request, scope, servletRequest);
            } catch (Exception e) {
                log.warn("Remote Search unavailable, falling back to MySQL", e);
                PictureSearchPageVO fallback = mysqlSearch(request, scope, servletRequest);
                fallback.setDegraded(true);
                return fallback;
            }
        }
        if ("SHADOW".equals(mode)) {
            PictureSearchPageVO mysql = mysqlSearch(request, scope, servletRequest);
            if (ThreadLocalRandom.current().nextDouble() < properties.getShadowSampleRate()) {
                try {
                    if (properties.getRemoteBaseUrl() != null && !properties.getRemoteBaseUrl().isBlank()) {
                        remoteSearch(request, scope, servletRequest);
                    } else {
                        esSearch(request, scope, servletRequest);
                    }
                } catch (Exception e) {
                    log.debug("picture search shadow request failed", e);
                }
            }
            return mysql;
        }
        try {
            ensureReadAlias();
            return esSearch(request, scope, servletRequest);
        } catch (Exception e) {
            if (!isAvailabilityFailure(e)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "搜索服务异常");
            }
            log.warn("Elasticsearch unavailable, falling back to MySQL", e);
            PictureSearchPageVO fallback = mysqlSearch(request, scope, servletRequest);
            fallback.setDegraded(true);
            return fallback;
        }
    }

    public List<String> suggestions(String keyword, Long spaceId, Integer limit, HttpServletRequest request) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int actualLimit = limit == null ? 8 : Math.min(Math.max(limit, 1), 10);
        SearchScope scope = authorize(spaceId, request);
        String mode = properties.getMode() == null ? "MYSQL" : properties.getMode().toUpperCase();
        if (!properties.isEnabled() || "MYSQL".equals(mode)) {
            return mysqlSuggestions(keyword, scope, actualLimit);
        }
        if ("REMOTE".equals(mode)) {
            try {
                return remoteClient.suggestions(keyword, spaceId, actualLimit);
            } catch (Exception e) {
                log.warn("Remote Search suggestions unavailable, falling back to MySQL", e);
                return mysqlSuggestions(keyword, scope, actualLimit);
            }
        }
        try {
            ensureReadAlias();
            ObjectNode body = objectMapper.createObjectNode();
            body.set("query", queryForScopeAndKeyword(keyword, scope, true));
            body.put("size", actualLimit);
            body.set("_source", objectMapper.createArrayNode().add("name"));
            body.set("sort", objectMapper.createArrayNode().addObject().putObject("_score").put("order", "desc"));
            JsonNode response = restClient.requestJson("POST", "/" + READ_ALIAS + "/_search", body);
            List<String> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonNode hit : response.path("hits").path("hits")) {
                String name = hit.path("_source").path("name").asText("");
                if (!name.isBlank() && seen.add(name)) {
                    result.add(name);
                }
            }
            return result;
        } catch (Exception e) {
            if (!isAvailabilityFailure(e)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "搜索建议异常");
            }
            return mysqlSuggestions(keyword, scope, actualLimit);
        }
    }

    private List<String> mysqlSuggestions(String keyword, SearchScope scope, int limit) {
        QueryWrapper<Picture> query = new QueryWrapper<>();
        query.select("name").and(wrapper -> wrapper.like("name", keyword)
                .or().like("introduction", keyword)
                .or().like("category", keyword)
                .or().like("tags", keyword))
                .eq("processStatus", 2).eq("isDelete", 0);
        if (scope.publicGallery()) {
            query.isNull("spaceId").eq("reviewStatus", 1);
        } else {
            query.eq("spaceId", scope.spaceId());
        }
        query.orderByDesc("editTime").last("limit " + limit);
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Picture picture : pictureService.list(query)) {
            if (picture.getName() != null && !picture.getName().isBlank() && seen.add(picture.getName())) {
                result.add(picture.getName());
            }
        }
        return result;
    }

    public PictureSearchFacetVO facets(Long spaceId, HttpServletRequest request) {
        SearchScope scope = authorize(spaceId, request);
        String mode = properties.getMode() == null ? "MYSQL" : properties.getMode().toUpperCase();
        if (!properties.isEnabled() || "MYSQL".equals(mode)) {
            return mysqlFacets(scope);
        }
        if ("REMOTE".equals(mode)) {
            try {
                SearchContractsV1.SearchFacetResponse response = remoteClient.facets(spaceId);
                PictureSearchFacetVO result = new PictureSearchFacetVO();
                response.categories().forEach(item -> result.getCategories().add(new PictureSearchFacetVO.FacetItemVO(item.value(), item.count())));
                response.tags().forEach(item -> result.getTags().add(new PictureSearchFacetVO.FacetItemVO(item.value(), item.count())));
                return result;
            } catch (Exception e) {
                log.warn("Remote Search facets unavailable, falling back to MySQL", e);
                return mysqlFacets(scope);
            }
        }
        try {
            ensureReadAlias();
            ObjectNode body = objectMapper.createObjectNode();
            body.set("query", queryForScopeAndKeyword(null, scope, false));
            body.put("size", 0);
            ObjectNode aggs = body.putObject("aggs");
            aggs.putObject("categories").putObject("terms").put("field", "category").put("size", 100);
            aggs.putObject("tags").putObject("terms").put("field", "tags").put("size", 100);
            JsonNode response = restClient.requestJson("POST", "/" + READ_ALIAS + "/_search", body);
            PictureSearchFacetVO result = new PictureSearchFacetVO();
            parseBuckets(response.path("aggregations").path("categories").path("buckets"), result.getCategories());
            parseBuckets(response.path("aggregations").path("tags").path("buckets"), result.getTags());
            return result;
        } catch (Exception e) {
            if (!isAvailabilityFailure(e)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "搜索聚合异常");
            }
            return mysqlFacets(scope);
        }
    }

    private PictureSearchFacetVO mysqlFacets(SearchScope scope) {
        QueryWrapper<Picture> query = new QueryWrapper<>();
        query.select("category", "tags").eq("processStatus", 2).eq("isDelete", 0);
        if (scope.publicGallery()) {
            query.isNull("spaceId").eq("reviewStatus", 1);
        } else {
            query.eq("spaceId", scope.spaceId());
        }
        Map<String, Long> categories = new HashMap<>();
        Map<String, Long> tags = new HashMap<>();
        for (Picture picture : pictureService.list(query)) {
            if (picture.getCategory() != null && !picture.getCategory().isBlank()) {
                categories.merge(picture.getCategory(), 1L, Long::sum);
            }
            if (picture.getTags() != null && !picture.getTags().isBlank()) {
                try {
                    for (String tag : cn.hutool.json.JSONUtil.toList(picture.getTags(), String.class)) {
                        if (tag != null && !tag.isBlank()) tags.merge(tag, 1L, Long::sum);
                    }
                } catch (Exception ignored) {
                    log.debug("ignore malformed picture tags while building facets, pictureId={}", picture.getId());
                }
            }
        }
        PictureSearchFacetVO result = new PictureSearchFacetVO();
        appendSortedBuckets(categories, result.getCategories());
        appendSortedBuckets(tags, result.getTags());
        return result;
    }

    private void appendSortedBuckets(Map<String, Long> counts, List<PictureSearchFacetVO.FacetItemVO> result) {
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(100)
                .forEach(entry -> result.add(new PictureSearchFacetVO.FacetItemVO(entry.getKey(), entry.getValue())));
    }

    private PictureSearchPageVO esSearch(PictureSearchRequest request, SearchScope scope,
                                          HttpServletRequest servletRequest) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("query", queryForScopeAndKeyword(request.getKeyword(), scope, false, request));
        body.put("from", (request.getCurrent() - 1) * request.getPageSize());
        body.put("size", request.getPageSize());
        body.set("sort", buildSort(request));
        ObjectNode highlight = body.putObject("highlight");
        highlight.putArray("pre_tags").add("__PICTURE_HL_START__");
        highlight.putArray("post_tags").add("__PICTURE_HL_END__");
        highlight.putObject("fields").putObject("name");
        highlight.putObject("fields").putObject("introduction");
        body.set("_source", objectMapper.createArrayNode().add("id"));

        JsonNode response = restClient.requestJson("POST", "/" + READ_ALIAS + "/_search", body);
        List<Long> ids = new ArrayList<>();
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> introductions = new HashMap<>();
        for (JsonNode hit : response.path("hits").path("hits")) {
            long id = hit.path("_id").asLong(hit.path("_source").path("id").asLong(0));
            if (id <= 0) {
                continue;
            }
            ids.add(id);
            JsonNode fragments = hit.path("highlight");
            if (fragments.path("name").isArray() && fragments.path("name").size() > 0) {
                names.put(id, safeHighlight(fragments.path("name").get(0).asText()));
            }
            if (fragments.path("introduction").isArray() && fragments.path("introduction").size() > 0) {
                introductions.put(id, safeHighlight(fragments.path("introduction").get(0).asText()));
            }
        }
        Map<Long, Picture> pictureMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Picture picture : pictureService.listByIds(ids)) {
                if (matchesScope(picture, scope)) {
                    pictureMap.put(picture.getId(), picture);
                }
            }
        }
        List<Picture> ordered = new ArrayList<>();
        for (Long id : ids) {
            Picture picture = pictureMap.get(id);
            if (picture != null) {
                ordered.add(picture);
            }
        }
        Page<Picture> page = new Page<>(request.getCurrent(), request.getPageSize(),
                response.path("hits").path("total").path("value").asLong(0));
        page.setRecords(ordered);
        Page<PictureVO> voPage = pictureService.getPictureVOPage(page, servletRequest);
        for (PictureVO vo : voPage.getRecords()) {
            vo.setHighlightName(names.get(vo.getId()));
            vo.setHighlightIntroduction(introductions.get(vo.getId()));
        }
        PictureSearchPageVO result = new PictureSearchPageVO();
        result.setRecords(voPage.getRecords());
        result.setTotal(voPage.getTotal());
        result.setCurrent(voPage.getCurrent());
        result.setPageSize(voPage.getSize());
        return result;
    }

    private PictureSearchPageVO remoteSearch(PictureSearchRequest request, SearchScope scope,
                                             HttpServletRequest servletRequest) throws IOException {
        SearchContractsV1.SearchQueryResponse response = remoteClient.search(request, scope.spaceId());
        List<Long> ids = response.hits().stream().map(SearchContractsV1.SearchHit::pictureId).toList();
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> introductions = new HashMap<>();
        response.hits().forEach(hit -> {
            names.put(hit.pictureId(), safeHighlight(hit.highlightName()));
            introductions.put(hit.pictureId(), safeHighlight(hit.highlightIntroduction()));
        });
        Map<Long, Picture> pictureMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Picture picture : pictureService.listByIds(ids)) {
                if (matchesScope(picture, scope)) pictureMap.put(picture.getId(), picture);
            }
        }
        List<Picture> ordered = new ArrayList<>();
        for (Long id : ids) {
            Picture picture = pictureMap.get(id);
            if (picture != null) ordered.add(picture);
        }
        Page<Picture> page = new Page<>(response.current(), response.pageSize(), response.total());
        page.setRecords(ordered);
        Page<PictureVO> voPage = pictureService.getPictureVOPage(page, servletRequest);
        for (PictureVO vo : voPage.getRecords()) {
            vo.setHighlightName(names.get(vo.getId()));
            vo.setHighlightIntroduction(introductions.get(vo.getId()));
        }
        PictureSearchPageVO result = new PictureSearchPageVO();
        result.setRecords(voPage.getRecords());
        result.setTotal(voPage.getTotal());
        result.setCurrent(voPage.getCurrent());
        result.setPageSize(voPage.getSize());
        return result;
    }

    private PictureSearchPageVO mysqlSearch(PictureSearchRequest request, SearchScope scope,
                                             HttpServletRequest servletRequest) {
        QueryWrapper<Picture> query = new QueryWrapper<>();
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            query.and(wrapper -> wrapper.like("name", request.getKeyword())
                    .or().like("introduction", request.getKeyword())
                    .or().like("category", request.getKeyword())
                    .or().like("tags", request.getKeyword()));
        }
        if (scope.publicGallery()) {
            query.isNull("spaceId").eq("reviewStatus", 1).eq("processStatus", 2);
        } else {
            query.eq("spaceId", scope.spaceId()).eq("processStatus", 2);
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) query.eq("category", request.getCategory());
        if (request.getName() != null && !request.getName().isBlank()) query.like("name", request.getName());
        if (request.getIntroduction() != null && !request.getIntroduction().isBlank()) query.like("introduction", request.getIntroduction());
        if (request.getPicFormat() != null && !request.getPicFormat().isBlank()) query.eq("picFormat", request.getPicFormat());
        if (request.getTags() != null) for (String tag : request.getTags()) if (tag != null && !tag.isBlank()) query.like("tags", "\"" + tag + "\"");
        if (request.getMinPicSize() != null) query.ge("picSize", request.getMinPicSize());
        if (request.getMaxPicSize() != null) query.le("picSize", request.getMaxPicSize());
        if (request.getMinPicWidth() != null) query.ge("picWidth", request.getMinPicWidth());
        if (request.getMaxPicWidth() != null) query.le("picWidth", request.getMaxPicWidth());
        if (request.getMinPicHeight() != null) query.ge("picHeight", request.getMinPicHeight());
        if (request.getMaxPicHeight() != null) query.le("picHeight", request.getMaxPicHeight());
        if (request.getStartEditTime() != null) query.ge("editTime", request.getStartEditTime());
        if (request.getEndEditTime() != null) query.lt("editTime", request.getEndEditTime());
        String sort = normalizeSort(request);
        if ("_score".equals(sort)) {
            query.orderByDesc("editTime");
        } else {
            query.orderBy(true, "ascend".equalsIgnoreCase(request.getSortOrder()), sort);
        }
        query.orderByDesc("id");
        Page<Picture> page = pictureService.page(new Page<>(request.getCurrent(), request.getPageSize()), query);
        Page<PictureVO> voPage = pictureService.getPictureVOPage(page, servletRequest);
        PictureSearchPageVO result = new PictureSearchPageVO();
        result.setRecords(voPage.getRecords());
        result.setTotal(voPage.getTotal());
        result.setCurrent(voPage.getCurrent());
        result.setPageSize(voPage.getSize());
        return result;
    }

    private ObjectNode queryForScopeAndKeyword(String keyword, SearchScope scope, boolean suggestion) {
        return queryForScopeAndKeyword(keyword, scope, suggestion, null);
    }

    private ObjectNode queryForScopeAndKeyword(String keyword, SearchScope scope, boolean suggestion,
                                               PictureSearchRequest request) {
        ObjectNode bool = objectMapper.createObjectNode();
        ArrayNode filter = bool.putObject("bool").putArray("filter");
        if (scope.publicGallery()) {
            filter.addObject().putObject("bool").putArray("must_not").addObject().putObject("exists").put("field", "spaceId");
            filter.addObject().putObject("term").put("reviewStatus", 1);
        } else {
            filter.addObject().putObject("term").put("spaceId", scope.spaceId());
        }
        filter.addObject().putObject("term").put("processStatus", 2);
        filter.addObject().putObject("term").put("isDelete", 0);
        ObjectNode queryBool = bool.path("bool").deepCopy();
        if (keyword != null && !keyword.isBlank()) {
            ObjectNode multi = objectMapper.createObjectNode();
            multi.put("query", keyword);
            if (suggestion) {
                // The edge-ngram sub-field keeps suggestions responsive while the
                // same scope filters prevent leaking names from another space.
                multi.putArray("fields").add("name.suggest^5").add("name^4")
                        .add("tags.text^3").add("category.text^2");
            } else {
                multi.putArray("fields").add("name^5").add("tags.text^3")
                        .add("category.text^2").add("introduction^2").add("picFormat.text");
            }
            multi.put("type", suggestion ? "bool_prefix" : "best_fields");
            queryBool.putArray("must").addObject().set("multi_match", multi);
        } else {
            queryBool.putArray("must").addObject().putObject("match_all");
        }
        if (request != null) {
            addOptionalFilters(queryBool.path("filter"), request);
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.set("bool", queryBool);
        return result;
    }

    private void addOptionalFilters(JsonNode node, PictureSearchRequest request) {
        if (!(node instanceof ArrayNode filter)) return;
        if (request.getCategory() != null && !request.getCategory().isBlank()) filter.addObject().putObject("term").put("category", request.getCategory());
        if (request.getName() != null && !request.getName().isBlank()) filter.addObject().putObject("match").put("name", request.getName());
        if (request.getIntroduction() != null && !request.getIntroduction().isBlank()) filter.addObject().putObject("match").put("introduction", request.getIntroduction());
        if (request.getPicFormat() != null && !request.getPicFormat().isBlank()) filter.addObject().putObject("term").put("picFormat", request.getPicFormat());
        if (request.getTags() != null) for (String tag : request.getTags()) if (tag != null && !tag.isBlank()) filter.addObject().putObject("term").put("tags", tag);
        addRange(filter, "picSize", request.getMinPicSize(), request.getMaxPicSize());
        addRange(filter, "picWidth", request.getMinPicWidth(), request.getMaxPicWidth());
        addRange(filter, "picHeight", request.getMinPicHeight(), request.getMaxPicHeight());
        addRange(filter, "editTime", request.getStartEditTime(), request.getEndEditTime());
    }

    private void addRange(ArrayNode filter, String field, Object gte, Object lte) {
        if (gte == null && lte == null) return;
        ObjectNode range = filter.addObject().putObject("range").putObject(field);
        if (gte != null) range.set("gte", objectMapper.valueToTree(gte));
        if (lte != null) range.set("lt", objectMapper.valueToTree(lte));
    }

    private ArrayNode buildSort(PictureSearchRequest request) {
        String sort = normalizeSort(request);
        ArrayNode result = objectMapper.createArrayNode();
        ObjectNode first = result.addObject().putObject(sort);
        first.put("order", "ascend".equalsIgnoreCase(request.getSortOrder()) ? "asc" : "desc");
        if ("_score".equals(sort)) {
            result.addObject().putObject("editTime").put("order", "desc");
        }
        result.addObject().putObject("id").put("order", "desc");
        return result;
    }

    private String normalizeSort(PictureSearchRequest request) {
        String sort = request.getSortField();
        if (sort == null || sort.isBlank()) return request.getKeyword() == null || request.getKeyword().isBlank() ? "createTime" : "_score";
        if (!SORT_FIELDS.contains(sort)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的排序字段");
        return sort;
    }

    private void validate(PictureSearchRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        if (request.getCurrent() < 1) request.setCurrent(1);
        if (request.getPageSize() < 1) request.setPageSize(10);
        if (request.getPageSize() > properties.getMaxPageSize()) throw new BusinessException(ErrorCode.PARAMS_ERROR, "单页最多查询 " + properties.getMaxPageSize() + " 条");
        long from = (long) (request.getCurrent() - 1) * request.getPageSize();
        if (from + request.getPageSize() > properties.getMaxResultWindow()) throw new BusinessException(ErrorCode.PARAMS_ERROR, "超出最大分页范围");
        normalizeSort(request);
    }

    private SearchScope authorize(Long spaceId, HttpServletRequest request) {
        if (spaceId == null) return new SearchScope(null, true, null);
        Space space = spaceService.getById(spaceId);
        if (space == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        User loginUser = userService.getLoginUser(request);
        if (!spaceUserAuthManager.getPermissionList(space, loginUser).contains(SpaceUserPermissionConstant.PICTURE_VIEW)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
        }
        return new SearchScope(spaceId, false, loginUser);
    }

    private boolean matchesScope(Picture picture, SearchScope scope) {
        if (picture.getIsDelete() != null && picture.getIsDelete() == 1) return false;
        if (!Integer.valueOf(2).equals(picture.getProcessStatus())) return false;
        if (scope.publicGallery()) {
            return picture.getSpaceId() == null && Integer.valueOf(1).equals(picture.getReviewStatus());
        }
        return scope.spaceId().equals(picture.getSpaceId());
    }

    private void parseBuckets(JsonNode buckets, List<PictureSearchFacetVO.FacetItemVO> result) {
        for (JsonNode bucket : buckets) {
            result.add(new PictureSearchFacetVO.FacetItemVO(bucket.path("key").asText(), bucket.path("doc_count").asLong()));
        }
    }

    private String safeHighlight(String value) {
        if (value == null) return null;
        return HtmlUtils.htmlEscape(value)
                .replace("__PICTURE_HL_START__", "<mark>")
                .replace("__PICTURE_HL_END__", "</mark>");
    }

    /** Core is read-only with respect to Elasticsearch; Search owns index creation and aliases. */
    private void ensureReadAlias() throws IOException {
        if (!restClient.exists(READ_ALIAS)) {
            throw new IOException("search read alias is not available");
        }
    }

    private boolean isAvailabilityFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof IOException) {
                if (current instanceof org.elasticsearch.client.ResponseException responseException) {
                    int status = responseException.getResponse().getStatusLine().getStatusCode();
                    return status == 429 || status == 502 || status == 503 || status == 504;
                }
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record SearchScope(Long spaceId, boolean publicGallery, User user) {
    }
}
