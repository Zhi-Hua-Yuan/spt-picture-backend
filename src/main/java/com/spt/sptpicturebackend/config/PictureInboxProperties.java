package com.spt.sptpicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "picture.inbox")
public class PictureInboxProperties {
    private long leaseSeconds = 60L;
}
