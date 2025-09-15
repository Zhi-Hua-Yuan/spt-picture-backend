package com.spt.sptpicturebackend.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author zhiyuan
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
