package com.spt.sptpicturebackend;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author zhiyuan
 */
@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class})
@EnableAsync
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.spt.sptpicturebackend.mapper")
public class SptPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SptPictureBackendApplication.class, args);
    }

}
