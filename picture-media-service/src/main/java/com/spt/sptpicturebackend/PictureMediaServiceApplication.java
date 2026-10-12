package com.spt.sptpicturebackend;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.spt.sptpicturebackend",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.spt\\.sptpicturebackend\\.service\\.InboxService"))
@MapperScan("com.spt.sptpicturebackend.media.persistence.mapper")
@EnableScheduling
public class PictureMediaServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PictureMediaServiceApplication.class, args);
    }
}
