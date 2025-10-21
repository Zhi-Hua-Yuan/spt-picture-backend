package com.spt.sptpicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.Space;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.spt.sptpicturebackend.service.SpaceAnalyzeService;
import com.spt.sptpicturebackend.service.SpaceService;
import com.spt.sptpicturebackend.service.UserService;

import javax.annotation.Resource;

/**
 * @author zhiyuan
 */
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    /**
     * 校验空间分析权限
     *
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser){
        // 校验权限
        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic() ){
            // 全空间分析或公共图库权限校验，仅管理员可访问
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR,"无权限访问公共图库");
        }else {
            // 私有空间权限校验
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR );
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            spaceService.checkSpaceAuth(space, loginUser);
        }
    }

    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }

}
