package com.spt.sptpicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.sptpicturebackend.model.entity.User;
import org.apache.ibatis.annotations.Select;

/**
* @author zhiyuan
* @description 针对表【user(用户表)】的数据库操作Mapper
* @createDate 2025-09-13 16:55:07
* @Entity com.spt.sptpicturebackend.model.entity.User
*/
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM user WHERE id = #{id} AND isDelete = 0 LIMIT 1 FOR UPDATE")
    User selectByIdForUpdate(Long id);

}




