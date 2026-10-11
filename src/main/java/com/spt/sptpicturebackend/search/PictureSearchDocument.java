package com.spt.sptpicturebackend.search;

import com.spt.sptpicturebackend.model.entity.Picture;

import java.util.LinkedHashMap;
import java.util.Map;

/** Flat source document used by Elasticsearch. */
public final class PictureSearchDocument {
    private PictureSearchDocument() {
    }

    public static Map<String, Object> from(Picture picture) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", picture.getId());
        doc.put("name", picture.getName());
        doc.put("introduction", picture.getIntroduction());
        doc.put("category", picture.getCategory());
        doc.put("tags", parseTags(picture.getTags()));
        doc.put("picFormat", picture.getPicFormat());
        doc.put("spaceId", picture.getSpaceId());
        doc.put("userId", picture.getUserId());
        doc.put("reviewStatus", picture.getReviewStatus());
        doc.put("processStatus", picture.getProcessStatus() == null ? 2 : picture.getProcessStatus());
        doc.put("isDelete", picture.getIsDelete());
        doc.put("picSize", picture.getPicSize());
        doc.put("picWidth", picture.getPicWidth());
        doc.put("picHeight", picture.getPicHeight());
        doc.put("picScale", picture.getPicScale());
        doc.put("picColor", picture.getPicColor());
        doc.put("createTime", picture.getCreateTime());
        doc.put("editTime", picture.getEditTime());
        doc.put("updateTime", picture.getUpdateTime());
        doc.put("version", picture.getVersion() == null ? 1L : picture.getVersion());
        return doc;
    }

    private static Object parseTags(String tags) {
        if (tags == null || tags.isBlank()) return java.util.List.of();
        try {
            return cn.hutool.json.JSONUtil.toList(tags, String.class);
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }
}
