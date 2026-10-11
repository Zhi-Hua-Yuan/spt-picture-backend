package com.spt.sptpicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the versioned picture search read model. */
@Data
@ConfigurationProperties(prefix = "picture.search")
public class PictureSearchProperties {

    private boolean enabled = true;
    /** MYSQL, SHADOW or ELASTIC. */
    private String mode = "MYSQL";
    private Elasticsearch elasticsearch = new Elasticsearch();
    private int maxPageSize = 20;
    private int maxResultWindow = 10_000;
    private double shadowSampleRate = 0.1D;

    @Data
    public static class Elasticsearch {
        private String endpoint = "http://localhost:9200";
        private String username;
        private String password;
        private int connectTimeoutMs = 500;
        private int socketTimeoutMs = 1200;
        private String indexPrefix = "picture_search";
    }
}
