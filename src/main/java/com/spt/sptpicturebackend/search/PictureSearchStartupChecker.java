package com.spt.sptpicturebackend.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class PictureSearchStartupChecker {
    @Resource private PictureSearchRestClient restClient;
    @Resource private PictureSearchIndexService indexService;

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        if (!restClient.isAvailable()) {
            log.warn("Elasticsearch unavailable at startup; picture search will remain degraded");
            return;
        }
        try {
            indexService.ensureActiveIndex();
            log.info("Elasticsearch picture search aliases are ready");
        } catch (Exception e) {
            log.warn("Elasticsearch index/alias check failed; main application remains available", e);
        }
    }
}
