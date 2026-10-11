package com.spt.sptpicturebackend.mapper;

import com.spt.sptpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
* @author zhiyuan
* @description 针对表【picture(图片表)】的数据库操作Mapper
* @createDate 2025-09-18 15:00:40
* @Entity com.spt.sptpicturebackend.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    /** Reads a row without MyBatis-Plus logical-delete filtering for search repair. */
    @Select("SELECT * FROM picture WHERE id = #{id} LIMIT 1")
    Picture selectByIdIncludingDeleted(Long id);

    @Select("SELECT * FROM picture WHERE id = #{id} AND isDelete = 0 LIMIT 1 FOR UPDATE")
    Picture selectByIdForUpdate(Long id);

    @Update("UPDATE picture SET isDelete = 1, version = COALESCE(version, 1) + 1, updateTime = NOW() WHERE id = #{id} AND isDelete = 0")
    int logicalDeleteAndBumpVersion(Long id);

    @Select("SELECT * FROM picture WHERE updateTime >= #{since} ORDER BY id LIMIT 100000")
    List<Picture> selectChangedSince(Date since);

}




