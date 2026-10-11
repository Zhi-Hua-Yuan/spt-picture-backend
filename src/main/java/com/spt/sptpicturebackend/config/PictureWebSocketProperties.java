package com.spt.sptpicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "picture.websocket")
public class PictureWebSocketProperties {
    private long lockTtlMs = 30_000L;
    private long lockRenewMs = 10_000L;
    private String channel = "picture:edit:events";
}
