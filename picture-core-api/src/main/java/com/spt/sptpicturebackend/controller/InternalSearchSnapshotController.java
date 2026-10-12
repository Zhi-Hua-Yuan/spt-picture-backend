package com.spt.sptpicturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.search.v1.SearchContractsV1;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Keyset snapshot endpoint used only by Search rebuild/reconcile jobs. */
@RestController
@RequestMapping("/internal/v1/search")
public class InternalSearchSnapshotController {
    @Resource private PictureMapper pictureMapper;
    @Resource private PictureSearchProperties properties;
    @Resource private ObjectMapper objectMapper;

    @GetMapping("/snapshots")
    public SearchContractsV1.SnapshotPage snapshots(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Long pictureId,
            @RequestParam(defaultValue = "500") Integer limit) {
        authorize(token);
        int pageSize = Math.min(Math.max(limit == null ? 500 : limit, 1), 500);
        QueryWrapper<Picture> query = new QueryWrapper<>();
        if (pictureId != null) {
            Picture picture = pictureMapper.selectByIdIncludingDeleted(pictureId);
            if (picture == null) return new SearchContractsV1.SnapshotPage(List.of(), pictureId, false);
            return new SearchContractsV1.SnapshotPage(List.of(toSnapshot(picture)), pictureId, false);
        }
        query.eq("isDelete", 0);
        if (afterId != null) query.gt("id", afterId);
        query.orderByAsc("id").last("limit " + pageSize);
        List<Picture> rows = pictureMapper.selectList(query);
        List<SearchContractsV1.Snapshot> snapshots = rows.stream().map(this::toSnapshot).toList();
        Long next = rows.isEmpty() ? afterId : rows.get(rows.size() - 1).getId();
        return new SearchContractsV1.SnapshotPage(snapshots, next, rows.size() == pageSize);
    }

    private SearchContractsV1.Snapshot toSnapshot(Picture picture) {
        List<String> tags = new ArrayList<>();
        if (picture.getTags() != null && !picture.getTags().isBlank()) {
            try { tags = objectMapper.readValue(picture.getTags(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)); }
            catch (Exception ignored) { }
        }
        return new SearchContractsV1.Snapshot(
                picture.getId(), picture.getVersion(), picture.getUrl(), picture.getName(), picture.getIntroduction(),
                picture.getCategory(), tags, picture.getPicSize(), picture.getPicWidth(), picture.getPicHeight(),
                picture.getPicScale(), picture.getPicFormat(), picture.getUserId(), picture.getSpaceId(), picture.getPicColor(),
                picture.getReviewStatus(), picture.getProcessStatus(), picture.getIsDelete(), instant(picture.getCreateTime()),
                instant(picture.getEditTime()), instant(picture.getUpdateTime()));
    }

    private java.time.Instant instant(java.util.Date value) { return value == null ? null : value.toInstant(); }

    private void authorize(String token) {
        byte[] expected = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }
}
