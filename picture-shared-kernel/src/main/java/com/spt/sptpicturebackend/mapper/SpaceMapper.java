package com.spt.sptpicturebackend.mapper;

import com.spt.sptpicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

/**
* @author zhiyuan
* @description 针对表【space(空间)】的数据库操作Mapper
* @createDate 2025-09-25 23:39:51
* @Entity com.spt.sptpicturebackend.model.entity.Space
*/
@Mapper
public interface SpaceMapper extends BaseMapper<Space> {

    @Select("SELECT * FROM space WHERE id = #{id} AND isDelete = 0 LIMIT 1 FOR UPDATE")
    Space selectByIdForUpdate(Long id);

}




