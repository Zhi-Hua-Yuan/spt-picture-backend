package com.spt.sptpicturebackend.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import com.spt.sptpicturebackend.mapper.SearchIndexStateMapper;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.SearchIndexState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PictureSearchIndexService {

    public static final String READ_ALIAS = "picture_search_read";
    public static final String WRITE_ALIAS = "picture_search_write";

    @Resource
    private PictureSearchRestClient restClient;

    @Resource
    private PictureSearchProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private SearchIndexStateMapper stateMapper;

    public String ensureActiveIndex() throws IOException {
        if (restClient.exists(READ_ALIAS)) {
            return READ_ALIAS;
        }
        String index = newIndexName();
        createIndex(index);
        switchAliases(index);
        return index;
    }

    public String createRebuildIndex() throws IOException {
        String index = newIndexName();
        createIndex(index);
        SearchIndexState state = stateMapper.selectById(1L);
        if (state == null) {
            state = new SearchIndexState();
            state.setId(1L);
            stateMapper.insert(state);
        }
        state.setBuildingIndex(index);
        stateMapper.updateById(state);
        return index;
    }

    public void finishRebuild(String newIndex) throws IOException {
        switchAliases(newIndex);
        clearBuildingIndex(newIndex);
    }

    public void cancelRebuild(String index) {
        clearBuildingIndex(index);
    }

    public void indexPicture(Picture picture) throws IOException {
        if (picture == null || picture.getId() == null) {
            return;
        }
        long version = picture.getVersion() == null ? 1L : picture.getVersion();
        String id = String.valueOf(picture.getId());
        if (Integer.valueOf(1).equals(picture.getIsDelete())) {
            restClient.delete(WRITE_ALIAS, id, version);
            indexBuildingDelete(id, version);
            return;
        }
        restClient.index(WRITE_ALIAS, id, PictureSearchDocument.from(picture), version);
        String building = currentBuildingIndex();
        if (building != null) {
            restClient.index(building, id, PictureSearchDocument.from(picture), version);
        }
    }

    public void deletePicture(Picture picture) throws IOException {
        if (picture == null || picture.getId() == null) {
            return;
        }
        long version = picture.getVersion() == null ? 1L : picture.getVersion();
        String id = String.valueOf(picture.getId());
        restClient.delete(WRITE_ALIAS, id, version);
        indexBuildingDelete(id, version);
    }

    public void deletePictureById(Long pictureId, long version) throws IOException {
        if (pictureId == null) return;
        String id = String.valueOf(pictureId);
        restClient.delete(WRITE_ALIAS, id, version);
        indexBuildingDelete(id, version);
    }

    public void bulkIndex(String index, List<Picture> pictures) throws IOException {
        List<PictureSearchRestClient.PictureSearchBulkItem> batch = new ArrayList<>();
        for (Picture picture : pictures) {
            if (picture.getId() == null || Integer.valueOf(1).equals(picture.getIsDelete())) {
                continue;
            }
            batch.add(new PictureSearchRestClient.PictureSearchBulkItem(
                    picture.getId(), picture.getVersion() == null ? 1L : picture.getVersion(),
                    PictureSearchDocument.from(picture)));
        }
        if (batch.isEmpty()) {
            return;
        }
        JsonNode result = restClient.bulkIndex(index, batch);
        if (restClient.hasNonVersionFailure(result)) {
            throw new IOException("Elasticsearch bulk index contains failures: " + result);
        }
    }

    private void indexBuildingDelete(String id, long version) throws IOException {
        String building = currentBuildingIndex();
        if (building != null) {
            restClient.delete(building, id, version);
        }
    }

    private String currentBuildingIndex() {
        SearchIndexState state = stateMapper.selectById(1L);
        return state == null ? null : state.getBuildingIndex();
    }

    private void clearBuildingIndex(String index) {
        SearchIndexState state = stateMapper.selectById(1L);
        if (state != null && index.equals(state.getBuildingIndex())) {
            state.setBuildingIndex(null);
            state.setRebuildTaskId(null);
            stateMapper.updateById(state);
        }
    }

    private void createIndex(String index) throws IOException {
        String mapping = """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0,
                    "refresh_interval": "1s",
                    "analysis": {
                      "filter": {
                        "picture_edge_ngram": {"type":"edge_ngram", "min_gram":1, "max_gram":20}
                      },
                      "analyzer": {
                        "picture_suggest_index": {"type":"custom", "tokenizer":"ik_max_word", "filter":["lowercase","picture_edge_ngram"]},
                        "picture_suggest_search": {"type":"custom", "tokenizer":"ik_smart", "filter":["lowercase"]}
                      }
                    }
                  },
                  "mappings": {
                    "dynamic": "strict",
                    "properties": {
                      "id":{"type":"long"},
                      "name":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart","fields":{"keyword":{"type":"keyword","ignore_above":256},"suggest":{"type":"text","analyzer":"picture_suggest_index","search_analyzer":"picture_suggest_search"}}},
                      "introduction":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart","fields":{"keyword":{"type":"keyword","ignore_above":1024}}},
                      "category":{"type":"keyword","fields":{"text":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"}}},
                      "tags":{"type":"keyword","fields":{"text":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"}}},
                      "picFormat":{"type":"keyword","fields":{"text":{"type":"text","analyzer":"standard"}}},
                      "spaceId":{"type":"long"},"userId":{"type":"long"},"reviewStatus":{"type":"integer"},"processStatus":{"type":"integer"},"isDelete":{"type":"byte"},
                      "picSize":{"type":"long"},"picWidth":{"type":"integer"},"picHeight":{"type":"integer"},"picScale":{"type":"double"},"picColor":{"type":"keyword"},
                      "createTime":{"type":"date"},"editTime":{"type":"date"},"updateTime":{"type":"date"},"version":{"type":"long"}
                    }
                  }
                }
                """;
        Map<String, Object> body = objectMapper.convertValue(objectMapper.readTree(mapping), Map.class);
        JsonNode result = restClient.requestJson("PUT", "/" + index, body);
        if (!result.path("acknowledged").asBoolean(true)) {
            throw new IOException("Elasticsearch index creation was not acknowledged: " + result);
        }
    }

    private void switchAliases(String newIndex) throws IOException {
        List<Map<String, Object>> actions = new ArrayList<>();
        for (String alias : List.of(READ_ALIAS, WRITE_ALIAS)) {
            try {
                JsonNode current = restClient.requestJson("GET", "/_alias/" + alias, null);
                Iterator<String> names = current.fieldNames();
                while (names.hasNext()) {
                    actions.add(Map.of("remove", Map.of("index", names.next(), "alias", alias)));
                }
            } catch (org.elasticsearch.client.ResponseException e) {
                if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                    throw e;
                }
            }
            Map<String, Object> add = new java.util.HashMap<>();
            add.put("index", newIndex);
            add.put("alias", alias);
            if (WRITE_ALIAS.equals(alias)) {
                add.put("is_write_index", true);
            }
            actions.add(Map.of("add", add));
        }
        JsonNode response = restClient.requestJson("POST", "/_aliases", Map.of("actions", actions));
        if (response.path("errors").asBoolean(false)) {
            throw new IOException("Elasticsearch alias switch failed: " + response);
        }
    }

    private String newIndexName() {
        return properties.getElasticsearch().getIndexPrefix() + "_v"
                + Instant.now().toEpochMilli() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
