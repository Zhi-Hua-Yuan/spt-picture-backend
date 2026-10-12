package com.spt.sptpicturebackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * @author zhiyuan
 */
@Data
@Component
@ConfigurationProperties(prefix = "cos.client")
public class CosClientConfig {

    private String secretId;

    private String secretKey;

    private String bucketName;

    private String region;

    private String host;

    @Bean
    COSClient cosClient() {
        // 设置用户身份信息
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 设置 bucket 区域
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 生成 cos 客户端
        return new COSClient(cred, clientConfig);
    }
}
