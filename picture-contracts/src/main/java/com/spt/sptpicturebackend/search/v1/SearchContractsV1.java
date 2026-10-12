package com.spt.sptpicturebackend.search.v1;

import java.time.Instant;
import java.util.List;

/** Internal Core/Search HTTP DTOs. They never cross the public API boundary. */
public final class SearchContractsV1 {
    private SearchContractsV1() {
    }

    public record SearchQueryRequest(
            String keyword,
            String name,
            String introduction,
            Long spaceId,
            String scope,
            String category,
            List<String> tags,
            String picFormat,
            Long minPicSize,
            Long maxPicSize,
            Integer minPicWidth,
            Integer maxPicWidth,
            Integer minPicHeight,
            Integer maxPicHeight,
            Instant startEditTime,
            Instant endEditTime,
            long current,
            long pageSize,
            String sortField,
            String sortOrder
    ) {
    }

    public record SearchQueryResponse(List<SearchHit> hits, long total, long current, long pageSize) {
    }

    public record SearchHit(Long pictureId, String highlightName, String highlightIntroduction) {
    }

    public record SearchFacetResponse(List<FacetItem> categories, List<FacetItem> tags) {
    }

    public record FacetItem(String value, long count) {
    }

    public record SnapshotPage(
            List<Snapshot> items,
            Long nextAfterId,
            boolean hasMore
    ) {
    }

    public record Snapshot(
            Long pictureId,
            Long version,
            String url,
            String name,
            String introduction,
            String category,
            List<String> tags,
            Long picSize,
            Integer picWidth,
            Integer picHeight,
            Double picScale,
            String picFormat,
            Long userId,
            Long spaceId,
            String picColor,
            Integer reviewStatus,
            Integer processStatus,
            Integer isDelete,
            Instant createTime,
            Instant editTime,
            Instant updateTime
    ) {
    }
}
