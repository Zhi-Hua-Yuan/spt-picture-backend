package com.spt.sptpicturebackend.media.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.sptpicturebackend.media.persistence.entity.MediaInboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaInboxEventMapper extends BaseMapper<MediaInboxEvent> {
}
