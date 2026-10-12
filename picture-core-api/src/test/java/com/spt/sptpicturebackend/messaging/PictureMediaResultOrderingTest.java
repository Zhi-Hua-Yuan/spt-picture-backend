package com.spt.sptpicturebackend.messaging;

import com.spt.sptpicturebackend.model.entity.Picture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PictureMediaResultOrderingTest {
    @Test
    void staleResultFromEarlierVersionIsIgnoredAfterNewerResultWins() {
        Picture current = new Picture();
        current.setProcessTaskId(20L);
        current.setVersion(3L);

        assertThat(PictureMediaResultConsumer.accepts(current, 20L, 2L)).isFalse();
        assertThat(PictureMediaResultConsumer.accepts(current, 20L, 3L)).isTrue();
    }

    @Test
    void resultForAnotherTaskCannotMutateThePicture() {
        Picture current = new Picture();
        current.setProcessTaskId(20L);
        current.setVersion(1L);

        assertThat(PictureMediaResultConsumer.accepts(current, 21L, 1L)).isFalse();
    }
}
