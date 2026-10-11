package com.spt.sptpicturebackend.service;

import com.spt.sptpicturebackend.config.PictureInboxProperties;
import com.spt.sptpicturebackend.mapper.MqConsumeLogMapper;
import com.spt.sptpicturebackend.model.entity.MqConsumeLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private MqConsumeLogMapper mapper;

    private InboxService service;

    @BeforeEach
    void setUp() {
        PictureInboxProperties properties = new PictureInboxProperties();
        properties.setLeaseSeconds(60);
        service = new InboxService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    @Test
    void claimsNewEventAndMarksItSucceeded() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.update(any(), any())).thenReturn(1);

        assertThat(service.claim("picture-process", "event-1")).isTrue();
        service.markSucceeded("picture-process", "event-1");

        verify(mapper).insert(any(MqConsumeLog.class));
        verify(mapper, org.mockito.Mockito.times(2)).update(any(), any());
    }

    @Test
    void doesNotClaimCompletedEvent() {
        MqConsumeLog completed = new MqConsumeLog();
        completed.setStatus(InboxService.SUCCEEDED);
        when(mapper.selectOne(any())).thenReturn(completed);

        assertThat(service.claim("picture-process", "event-2")).isFalse();
        verify(mapper, never()).update(any(), any());
    }
}
