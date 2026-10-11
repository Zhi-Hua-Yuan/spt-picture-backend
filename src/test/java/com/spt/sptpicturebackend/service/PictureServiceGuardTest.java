package com.spt.sptpicturebackend.service;

import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.model.dto.picture.PictureUploadRequest;
import com.spt.sptpicturebackend.service.impl.PictureServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureServiceGuardTest {

    private final PictureServiceImpl service = new PictureServiceImpl();

    @Test
    void uploadRequiresLoginBeforeTouchingStorage() {
        assertThatThrownBy(() -> service.uploadPicture(new Object(), new PictureUploadRequest(), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteRejectsInvalidIdAndAnonymousCall() {
        assertThatThrownBy(() -> service.deletePicture(0, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deletePicture(1, null))
                .isInstanceOf(BusinessException.class);
    }
}
