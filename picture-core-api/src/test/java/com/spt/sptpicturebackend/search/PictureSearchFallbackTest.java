package com.spt.sptpicturebackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.sptpicturebackend.config.PictureSearchProperties;
import com.spt.sptpicturebackend.manager.auth.SpaceUserAuthManager;
import com.spt.sptpicturebackend.model.dto.picture.PictureSearchRequest;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.vo.picture.PictureSearchPageVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureVO;
import com.spt.sptpicturebackend.service.PictureService;
import com.spt.sptpicturebackend.service.SpaceService;
import com.spt.sptpicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PictureSearchFallbackTest {

    @Mock
    private PictureSearchRestClient restClient;
    @Mock
    private PictureService pictureService;
    @Mock
    private SpaceService spaceService;
    @Mock
    private UserService userService;
    @Mock
    private SpaceUserAuthManager spaceUserAuthManager;

    private PictureSearchService service;

    @BeforeEach
    void setUp() {
        PictureSearchProperties properties = new PictureSearchProperties();
        properties.setEnabled(true);
        properties.setMode("ELASTIC");

        service = new PictureSearchService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "restClient", restClient);
        ReflectionTestUtils.setField(service, "pictureService", pictureService);
        ReflectionTestUtils.setField(service, "spaceService", spaceService);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "spaceUserAuthManager", spaceUserAuthManager);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void returnsMysqlResultsMarkedAsDegradedWhenElasticIsUnavailable() throws Exception {
        doThrow(new IOException("elasticsearch is down")).when(restClient).exists("picture_search_read");

        Picture picture = new Picture();
        picture.setId(7L);
        Page<Picture> mysqlPage = new Page<>(1, 10, 1);
        mysqlPage.setRecords(List.of(picture));
        when(pictureService.page(any(Page.class), any())).thenReturn(mysqlPage);

        Page<PictureVO> voPage = new Page<>(1, 10, 1);
        voPage.setRecords(List.of(new PictureVO()));
        when(pictureService.getPictureVOPage(any(Page.class), any())).thenReturn(voPage);

        PictureSearchRequest request = new PictureSearchRequest();
        request.setKeyword("spring");
        PictureSearchPageVO result = service.search(request, new MockHttpServletRequest());

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
    }
}
