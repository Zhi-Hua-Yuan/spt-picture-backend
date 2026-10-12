package com.spt.sptpicturebackend.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.spt.sptpicturebackend.support.TestcontainersIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class cryptoTest extends TestcontainersIntegrationTest {

    @Test
    public void md5Test() {
        String originPassword = "12345678";
        String md5_1 = DigestUtil.md5Hex(originPassword);
        String md5_2 = DigestUtil.md5Hex(originPassword);
        System.out.println(md5_1);
        System.out.println(md5_2);
        Assertions.assertEquals(md5_1, md5_2);
    }
}
