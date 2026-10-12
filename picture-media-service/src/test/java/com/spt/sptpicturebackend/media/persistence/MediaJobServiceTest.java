package com.spt.sptpicturebackend.media.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.media.persistence.entity.MediaJob;
import com.spt.sptpicturebackend.media.persistence.mapper.MediaJobItemMapper;
import com.spt.sptpicturebackend.media.persistence.mapper.MediaJobMapper;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaJobServiceTest {
    @Mock private MediaJobMapper jobMapper;
    @Mock private MediaJobItemMapper itemMapper;

    @Test
    void sameRequestEventReusesLocalJobOnRedelivery() {
        MediaJob existing = new MediaJob(); existing.setId(42L); existing.setAttemptCount(1); existing.setStatus("FAILED");
        when(jobMapper.selectOne(any())).thenReturn(existing);
        MediaJobService service = new MediaJobService();
        ReflectionTestUtils.setField(service, "jobMapper", jobMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper().findAndRegisterModules());
        EventEnvelopeV1<PicturePayloadsV1.PictureProcessingRequested> event = new EventEnvelopeV1<>(
                "event-1", "picture.media.process.requested.v1", 1, Instant.now(), "t", "c", null, 7L, 1L,
                new PicturePayloadsV1.PictureProcessingRequested(7L, 8L, 1L, "key"));

        MediaJob result = service.startProcess(event, event.payload());

        assertThat(result).isSameAs(existing);
        assertThat(existing.getAttemptCount()).isEqualTo(2);
        assertThat(existing.getStatus()).isEqualTo("RUNNING");
        verify(jobMapper).updateById(existing);
    }
}
