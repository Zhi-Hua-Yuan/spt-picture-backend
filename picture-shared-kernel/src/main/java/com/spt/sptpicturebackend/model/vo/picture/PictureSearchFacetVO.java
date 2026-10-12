package com.spt.sptpicturebackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PictureSearchFacetVO implements Serializable {
    private List<FacetItemVO> categories = new ArrayList<>();
    private List<FacetItemVO> tags = new ArrayList<>();

    @Data
    public static class FacetItemVO implements Serializable {
        private String value;
        private long count;

        public FacetItemVO() {
        }

        public FacetItemVO(String value, long count) {
            this.value = value;
            this.count = count;
        }
    }
}
