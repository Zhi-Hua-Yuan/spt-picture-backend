package com.spt.sptpicturebackend.model.dto.picture;

import com.spt.sptpicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PictureSearchRequest extends PageRequest implements Serializable {

    private String keyword;
    /** Compatibility filters retained for the existing space search form. */
    private String name;
    private String introduction;
    private Long spaceId;
    private String category;
    private List<String> tags;
    private String picFormat;
    private Long minPicSize;
    private Long maxPicSize;
    private Integer minPicWidth;
    private Integer maxPicWidth;
    private Integer minPicHeight;
    private Integer maxPicHeight;
    private Date startEditTime;
    private Date endEditTime;

    private static final long serialVersionUID = 1L;
}
