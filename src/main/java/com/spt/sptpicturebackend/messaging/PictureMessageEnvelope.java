package com.spt.sptpicturebackend.messaging;

import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
public class PictureMessageEnvelope {
    private int schemaVersion = 1;
    private String eventId;
    private String eventType;
    private Long pictureId;
    private Long taskId;
    private Long pictureVersion;
    private Date occurredAt;
    private String traceId;
    private int retryCount;
    private Map<String, Object> data;
}
