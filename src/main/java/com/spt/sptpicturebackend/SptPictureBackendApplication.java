package com.spt.sptpicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author zhiyuan
 */
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.spt.sptpicturebackend.mapper")
public class SptPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SptPictureBackendApplication.class, args);
    }

}
