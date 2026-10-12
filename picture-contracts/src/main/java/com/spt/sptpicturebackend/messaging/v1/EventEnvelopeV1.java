package com.spt.sptpicturebackend.messaging.v1;

import java.time.Instant;

/**
 * Stable cross-service event envelope.  The payload is deliberately typed at
 * the producer/consumer boundary so a service cannot silently depend on a
 * shared database row or an unversioned map.
 */
public record EventEnvelopeV1<T>(
        String eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String traceId,
        String correlationId,
        String causationId,
        Long aggregateId,
        Long aggregateVersion,
        T payload
) {
    public static final int VERSION = 1;

    public EventEnvelopeV1 {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    public static <T> EventEnvelopeV1<T> create(
            String eventType,
            String traceId,
            String correlationId,
            String causationId,
            Long aggregateId,
            Long aggregateVersion,
            T payload
    ) {
        return new EventEnvelopeV1<>(
                java.util.UUID.randomUUID().toString(),
                eventType,
                VERSION,
                Instant.now(),
                traceId,
                correlationId,
                causationId,
                aggregateId,
                aggregateVersion,
                payload
        );
    }
}
