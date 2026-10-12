package com.spt.sptpicturebackend.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/** Creates a lazy, non-blocking-at-startup Elasticsearch low-level client. */
@Configuration
@EnableConfigurationProperties(PictureSearchProperties.class)
public class PictureSearchClientConfig {

    @Bean(name = "pictureSearchHttpClient", destroyMethod = "close")
    public RestClient pictureSearchHttpClient(PictureSearchProperties properties) {
        PictureSearchProperties.Elasticsearch es = properties.getElasticsearch();
        RestClientBuilder builder = RestClient.builder(HttpHost.create(es.getEndpoint()))
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(es.getConnectTimeoutMs())
                        .setConnectionRequestTimeout(es.getConnectTimeoutMs())
                        .setSocketTimeout(es.getSocketTimeoutMs()));
        if (es.getUsername() != null && !es.getUsername().isBlank()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(es.getUsername(), es.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        return builder.build();
    }

    /** Keep a single constant to make request construction consistent in services. */
    @Bean
    public RequestOptions pictureSearchRequestOptions() {
        return RequestOptions.DEFAULT;
    }
}
