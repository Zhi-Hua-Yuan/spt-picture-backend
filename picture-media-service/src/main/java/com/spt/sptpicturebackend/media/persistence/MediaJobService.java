package com.spt.sptpicturebackend.media.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.media.persistence.entity.MediaJob;
import com.spt.sptpicturebackend.media.persistence.entity.MediaJobItem;
import com.spt.sptpicturebackend.media.persistence.mapper.MediaJobItemMapper;
import com.spt.sptpicturebackend.media.persistence.mapper.MediaJobMapper;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

/** Local execution ledger for media work. It deliberately contains no Core entities. */
@Service
public class MediaJobService {
    public static final String PROCESS = "PROCESS";
    public static final String BATCH = "BATCH";
    public static final String FILE_DELETE = "FILE_DELETE";

    @Resource private MediaJobMapper jobMapper;
    @Resource private MediaJobItemMapper itemMapper;
    @Resource private ObjectMapper objectMapper;

    @Transactional
    public MediaJob startProcess(EventEnvelopeV1<?> raw, PicturePayloadsV1.PictureProcessingRequested request) {
        MediaJob job = findByRequest(raw.eventId());
        if (job == null) {
            job = base(raw.eventId(), request.taskId(), PROCESS, request.pictureId(), request.expectedVersion(), request);
            jobMapper.insert(job);
        } else {
            job.setStatus("RUNNING");
            job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
            jobMapper.updateById(job);
        }
        return job;
    }

    @Transactional
    public MediaJob startBatch(EventEnvelopeV1<?> raw, PicturePayloadsV1.BatchImportRequested request,
                               List<PicturePayloadsV1.RetryItem> items) {
        MediaJob job = findByRequest(raw.eventId());
        if (job == null) {
            job = base(raw.eventId(), request.taskId(), BATCH, null, 1L, request);
            jobMapper.insert(job);
        } else {
            job.setStatus("RUNNING");
            job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
            jobMapper.updateById(job);
        }
        for (PicturePayloadsV1.RetryItem item : items) {
            MediaJobItem existing = itemMapper.selectOne(new QueryWrapper<MediaJobItem>()
                    .eq("media_job_id", job.getId()).eq("sequence_no", item.sequenceNo()).last("LIMIT 1"));
            if (existing == null) {
                MediaJobItem row = new MediaJobItem();
                row.setMediaJobId(job.getId()); row.setSourceItemId(item.itemId());
                row.setSequenceNo(item.sequenceNo()); row.setSourceUrl(item.sourceUrl());
                row.setStatus("PENDING"); row.setAttemptCount(0);
                itemMapper.insert(row);
            }
        }
        return job;
    }

    @Transactional
    public MediaJob startFileDelete(EventEnvelopeV1<?> raw, PicturePayloadsV1.FilesDeleteRequested request) {
        long sourceTask = raw.aggregateId() == null ? Math.abs(raw.eventId().hashCode()) + 1L : raw.aggregateId();
        MediaJob job = findByRequest(raw.eventId());
        if (job == null) {
            job = base(raw.eventId(), sourceTask, FILE_DELETE, raw.aggregateId(), raw.aggregateVersion(), request);
            jobMapper.insert(job);
        } else {
            job.setStatus("RUNNING"); job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
            jobMapper.updateById(job);
        }
        return job;
    }

    public void markSucceeded(MediaJob job) { update(job, "SUCCEEDED", null, null); }
    public void markFailed(MediaJob job, String code, String message) { update(job, "FAILED", code, message); }
    public void markBatchPartial(MediaJob job) { update(job, "PARTIAL_FAILED", null, null); }

    public void markItem(MediaJob job, PicturePayloadsV1.RetryItem source, PicturePayloadsV1.BatchItemPrepared result) {
        MediaJobItem item = itemMapper.selectOne(new QueryWrapper<MediaJobItem>()
                .eq("media_job_id", job.getId()).eq("sequence_no", source.sequenceNo()).last("LIMIT 1"));
        if (item == null) return;
        item.setStatus(result.success() ? "SUCCEEDED" : "FAILED");
        item.setOriginalObjectKey(result.originalObjectKey()); item.setDisplayObjectKey(result.displayObjectKey());
        item.setThumbnailObjectKey(result.thumbnailObjectKey()); item.setErrorMessage(result.errorMessage());
        item.setAttemptCount((item.getAttemptCount() == null ? 0 : item.getAttemptCount()) + 1);
        itemMapper.updateById(item);
    }

    private MediaJob findByRequest(String eventId) {
        return jobMapper.selectOne(new QueryWrapper<MediaJob>().eq("request_event_id", eventId).last("LIMIT 1"));
    }

    private MediaJob base(String eventId, Long sourceTaskId, String type, Long pictureId, Long version, Object payload) {
        MediaJob job = new MediaJob();
        job.setSourceTaskId(sourceTaskId); job.setJobType(type); job.setPictureId(pictureId);
        job.setExpectedVersion(version); job.setRequestEventId(eventId); job.setStatus("RUNNING");
        job.setAttemptCount(1); job.setRequestPayload(write(payload)); job.setCreateTime(new Date()); job.setUpdateTime(new Date());
        return job;
    }

    private void update(MediaJob job, String status, String code, String message) {
        job.setStatus(status); job.setErrorCode(code); job.setErrorMessage(shorten(message)); job.setUpdateTime(new Date());
        jobMapper.updateById(job);
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("无法记录媒体任务", error); }
    }

    private String shorten(String value) { return value == null ? null : value.substring(0, Math.min(value.length(), 1900)); }
}
