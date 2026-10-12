package com.spt.sptpicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "picture.async")
public class PictureAsyncProperties {
    /** Legacy umbrella switch retained for backwards compatible Core API configuration. */
    private boolean enabled = true;
    private boolean outboxEnabled = true;
    private boolean processEnabled = true;
    private boolean batchEnabled = true;
    private boolean fileDeleteEnabled = true;
    private boolean searchIndexEnabled = true;
    private boolean indexMaintenanceEnabled = true;
    private boolean reconcileEnabled = true;
    private int publisherBatchSize = 100;
    private int processConcurrency = 2;
    private int batchConcurrency = 2;
    private int fileDeleteConcurrency = 2;
    private int searchIndexConcurrency = 2;
    private int indexMaintenanceConcurrency = 1;
    private int outboxRetentionDays = 7;
    private int consumeLogRetentionDays = 30;
}
