package com.spt.sptpicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.spt.sptpicturebackend",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.spt\\.sptpicturebackend\\.service\\.InboxService"))
@EnableScheduling
@MapperScan("com.spt.sptpicturebackend.search.persistence")
public class PictureSearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PictureSearchServiceApplication.class, args);
    }
}
