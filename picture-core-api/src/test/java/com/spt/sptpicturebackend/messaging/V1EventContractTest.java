package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PictureEventTypesV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class V1EventContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsVersionedBatchPayloadWithoutLegacyFields() throws Exception {
        EventEnvelopeV1<PicturePayloadsV1.BatchImportRequested> event = new EventEnvelopeV1<>(
                "event-1", PictureEventTypesV1.BATCH_IMPORT_REQUESTED, EventEnvelopeV1.VERSION,
                Instant.parse("2026-10-12T03:00:00Z"), "trace-1", "task-1", null, 88L, 1L,
                new PicturePayloadsV1.BatchImportRequested(88L, 9L, 7L, "cats", 2, "cat-", List.of()));

        String json = objectMapper.writeValueAsString(event);
        EventEnvelopeV1<?> decoded = objectMapper.readValue(json, EventEnvelopeV1.class);

        assertThat(decoded.schemaVersion()).isEqualTo(1);
        assertThat(decoded.eventType()).isEqualTo(PictureEventTypesV1.BATCH_IMPORT_REQUESTED);
        assertThat(decoded.aggregateId()).isEqualTo(88L);
        assertThat(decoded.payload()).isNotNull();
        assertThat(json).doesNotContain("pictureVersion").doesNotContain("taskId\":null");
    }
}
