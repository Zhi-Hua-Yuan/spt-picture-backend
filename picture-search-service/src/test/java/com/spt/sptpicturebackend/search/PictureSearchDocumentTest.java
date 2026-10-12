package com.spt.sptpicturebackend.search;

import com.spt.sptpicturebackend.model.entity.Picture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PictureSearchDocumentTest {
    @Test
    void mapsTagsAndDefaultsProcessingVersion() {
        Picture picture = new Picture();
        picture.setId(7L);
        picture.setName("春天");
        picture.setTags("[\"风景\",\"高清\"]");
        var document = PictureSearchDocument.from(picture);
        assertThat(document.get("id")).isEqualTo(7L);
        assertThat(document.get("tags")).asList().containsExactly("风景", "高清");
        assertThat(document.get("processStatus")).isEqualTo(2);
        assertThat(document.get("version")).isEqualTo(1L);
    }
}
