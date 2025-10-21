package com.spt.sptpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.mapper.SpaceMapper;
import com.spt.sptpicturebackend.model.dto.space.SpaceAddRequest;
import com.spt.sptpicturebackend.model.dto.space.SpaceQueryRequest;
import com.spt.sptpicturebackend.model.entity.Space;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.enums.SpaceLevelEnum;
import com.spt.sptpicturebackend.model.vo.space.SpaceVO;
import com.spt.sptpicturebackend.service.SpaceService;
import com.spt.sptpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author zhiyuan
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-09-25 23:39:51
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;


    /**
     * 获取查询包装类
     *
     * @param spaceQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        // todo 完善
        return null;
    }

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1.填充参数默认值
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 填充数据
        this.fillSpaceBySpaceLevel(space);
        // 2.校验参数
        this.validSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 3.校验权限，非管理员只能创建普通级别空间
        if (!userService.isAdmin(loginUser) && SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 4.控制同一个用户只能创建一个私有空间
        String lock = userId.toString().intern();
        synchronized (lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("userId", userId);
                boolean exists = this.exists(queryWrapper);
                ThrowUtils.throwIf(!exists, ErrorCode.OPERATION_ERROR, "每个用户仅能拥有一个私有空间");
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                return space.getId();
            });
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        // 第二种方式，将用户信息放入 set 集合中，查找效率更高
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        // 如果空间列表为空，则直接返回
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserMap = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserMap.containsKey(userId)) {
                user = userIdUserMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;

    }

    /**
     * 空间信息校验
     *
     * @param space
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

    /**
     * 填充空间信息
     *
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 空间信息脱敏
     *
     * @param space
     * @param request
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            spaceVO.setUser(userService.getUserVO(user));
        }
        return spaceVO;
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人或者管理员有资格
        if (!userService.isAdmin(loginUser) && !loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

}




