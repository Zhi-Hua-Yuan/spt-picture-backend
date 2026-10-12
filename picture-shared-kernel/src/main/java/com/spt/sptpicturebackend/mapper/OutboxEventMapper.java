package com.spt.sptpicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.sptpicturebackend.model.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
}
