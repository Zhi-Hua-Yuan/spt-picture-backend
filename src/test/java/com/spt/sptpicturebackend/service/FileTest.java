package com.spt.sptpicturebackend.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.MalformedURLException;
import java.net.URL;

@SpringBootTest
@Slf4j
public class FileTest {
    @Test
    public void testUrl() {
        String url = "codefatherour";
        try {
            URL url1 = new URL(url);
            System.out.println(url1);
        } catch (MalformedURLException e) {
            log.error("url错误: {}", url);
        }
    }
}
