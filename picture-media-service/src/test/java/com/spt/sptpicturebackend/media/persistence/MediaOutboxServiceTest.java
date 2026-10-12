package com.spt.sptpicturebackend.media.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.media.persistence.entity.MediaOutboxEvent;
import com.spt.sptpicturebackend.media.persistence.mapper.MediaOutboxEventMapper;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MediaOutboxServiceTest {
    @Mock private MediaOutboxEventMapper mapper;
    @Mock private RabbitTemplate rabbitTemplate;

    @Test
    void resultIsPersistedBeforeBrokerDelivery() {
        MediaOutboxService service = new MediaOutboxService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper().findAndRegisterModules());
        EventEnvelopeV1<String> cause = new EventEnvelopeV1<>("cause-1", "request", 1, Instant.now(), "trace", "corr", null, 9L, 1L, "payload");

        String eventId = service.enqueue("result", "picture.media.result.v1", cause, 9L, 2L, "done");

        ArgumentCaptor<MediaOutboxEvent> captor = ArgumentCaptor.forClass(MediaOutboxEvent.class);
        verify(mapper).insert(captor.capture());
        MediaOutboxEvent row = captor.getValue();
        assertThat(eventId).isEqualTo(row.getEventId());
        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getExchangeName()).isEqualTo("picture.events.v1");
        assertThat(row.getPayload()).contains("cause-1").contains("result");
    }
}
