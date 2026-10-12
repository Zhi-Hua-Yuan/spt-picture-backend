package com.spt.sptpicturebackend.service;

import com.spt.sptpicturebackend.support.TestcontainersIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;

@Slf4j
public class FileTest extends TestcontainersIntegrationTest {
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
