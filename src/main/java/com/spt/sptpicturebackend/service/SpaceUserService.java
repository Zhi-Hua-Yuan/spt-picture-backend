package com.spt.sptpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.sptpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.spt.sptpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.spt.sptpicturebackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.spring.service.IService;
import com.spt.sptpicturebackend.model.vo.spaceuser.SpaceUserVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author zhiyuan
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-10-31 17:16:53
*/
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 添加空间用户
     *
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 验证空间用户信息
     *
     * @param spaceUser
     * @param add
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取查询条件
     *
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 获取空间用户封装类
     *
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间用户列表封装类
     *
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
