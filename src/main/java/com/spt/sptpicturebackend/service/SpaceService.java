package com.spt.sptpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.baomidou.mybatisplus.spring.service.IService;
import com.spt.sptpicturebackend.model.dto.space.SpaceAddRequest;
import com.spt.sptpicturebackend.model.dto.space.SpaceQueryRequest;
import com.spt.sptpicturebackend.model.entity.Space;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.space.SpaceVO;


import jakarta.servlet.http.HttpServletRequest;

/**
 * @author zhiyuan
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-09-25 23:39:51
 */
public interface SpaceService extends IService<Space> {

    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 添加空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /** 删除空空间并在同一事务中移除有效成员关系。 */
    boolean deleteSpace(Long spaceId);

    /**
     * 分页获取空间列表
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 空间信息校验
     *
     * @param space
     */
    void validSpace(Space space, boolean add);

    /**
     * 填充空间信息
     *
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 空间信息脱敏
     *
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    void checkSpaceAuth(User loginUser, Space space);

}
