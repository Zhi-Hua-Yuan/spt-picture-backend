package com.spt.sptpicturebackend.manager.upload;

import com.spt.sptpicturebackend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlPictureUploadTest {
    private final UrlPictureUpload uploader = new UrlPictureUpload();

    @Test
    void rejectsLoopbackAddressBeforeNetworkRequest() {
        assertThatThrownBy(() -> uploader.validPicture("http://127.0.0.1/image.png"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNonHttpProtocol() {
        assertThatThrownBy(() -> uploader.validPicture("file:///etc/passwd"))
                .isInstanceOf(BusinessException.class);
    }
}
