package com.spt.sptpicturebackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.mapper.MqConsumeLogMapper;
import com.spt.sptpicturebackend.mapper.OutboxEventMapper;
import com.spt.sptpicturebackend.model.entity.OutboxEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventMapper outboxEventMapper;
    @Mock
    private MqConsumeLogMapper mqConsumeLogMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxEventService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OutboxEventService();
        ReflectionTestUtils.setField(service, "outboxEventMapper", outboxEventMapper);
        ReflectionTestUtils.setField(service, "mqConsumeLogMapper", mqConsumeLogMapper);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "meterRegistry", new SimpleMeterRegistry());
    }

    @Test
    void createsVersionedEnvelopeInsideTheOutboxRecord() throws Exception {
        String eventId = service.create(
                "PICTURE_CHANGED", "picture.changed", 7L, 3L, 7L, null,
                Map.of("reason", "upload"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventMapper).insert(captor.capture());
        OutboxEvent event = captor.getValue();
        JsonNode envelope = objectMapper.readTree(event.getPayload());

        assertThat(eventId).isEqualTo(event.getEventId());
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(envelope.path("eventId").asText()).isEqualTo(eventId);
        assertThat(envelope.path("eventType").asText()).isEqualTo("PICTURE_CHANGED");
        assertThat(envelope.path("pictureId").asLong()).isEqualTo(7L);
        assertThat(envelope.path("pictureVersion").asLong()).isEqualTo(3L);
        assertThat(envelope.path("data").path("reason").asText()).isEqualTo("upload");
    }
}
