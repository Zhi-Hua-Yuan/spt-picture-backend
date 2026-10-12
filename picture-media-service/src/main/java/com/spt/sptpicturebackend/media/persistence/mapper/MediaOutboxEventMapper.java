package com.spt.sptpicturebackend.media.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.sptpicturebackend.media.persistence.entity.MediaOutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaOutboxEventMapper extends BaseMapper<MediaOutboxEvent> {
}
