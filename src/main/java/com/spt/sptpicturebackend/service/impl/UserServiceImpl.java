package com.spt.sptpicturebackend.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.spt.sptpicturebackend.constant.UserConstant;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.manager.auth.StpKit;
import com.spt.sptpicturebackend.mapper.UserMapper;
import com.spt.sptpicturebackend.mapper.SpaceMapper;
import com.spt.sptpicturebackend.mapper.SpaceUserMapper;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.model.dto.user.UserQueryRequest;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.user.LoginUserVO;
import com.spt.sptpicturebackend.model.vo.user.UserVO;
import com.spt.sptpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author zhiyuan
 * @description 针对表【user(用户表)】的数据库操作Service实现
 * @createDate 2025-09-13 16:55:07
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @jakarta.annotation.Resource
    private SpaceMapper spaceMapper;

    @jakarta.annotation.Resource
    private PictureMapper pictureMapper;

    @jakarta.annotation.Resource
    private SpaceUserMapper spaceUserMapper;

    @jakarta.annotation.Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        // 非空
        if (StrUtil.hasEmpty(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号、密码或校验密码不能为空");
        }
        // 账号不小于 6 位，密码和校验密码不小于 8 位
        if (userAccount.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能小于6位");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码或校验密码不能小于8位");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码和校验密码不同");
        }
        // 账号不能已经存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        // 2. 加密
        String encryptPassword = DigestUtil.md5Hex(userPassword + UserConstant.SALT);
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
        }
        // 4.返回新用户的 id
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param userAccount
     * @param userPassword
     * @param request
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        // 非空
        if (StrUtil.hasEmpty(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号和密码不能为空");
        }
        // 账号不小于 6 位，密码和校验密码不小于 8 位
        if (userAccount.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能小于6位");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码或校验密码不能小于8位");
        }
        // 2. 比对数据库
        String encryptPassword = DigestUtil.md5Hex(userPassword + UserConstant.SALT);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.getOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 统一使用 Sa-Token Redis 登录态。请求参数保留是为了兼容现有 Service 接口，
        // 不再把用户对象写入 HttpSession，避免多套会话状态不一致。
        StpKit.SPACE.login(user.getId());
        // 4. 返回脱敏后的用户信息
        return this.getLoginUserVO(user);
    }

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        StpKit.SPACE.logout();
        return true;
    }

    /**
     * 获取当前登录用户的脱敏信息
     *
     * @param user
     * @return
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 用户信息脱敏
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 用户列表脱敏
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (ObjUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    /**
     * 获取当前登录用户的详细信息
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 判断是否已登录
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!StpKit.SPACE.isLogin()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Long loginId = StpKit.SPACE.getLoginIdAsLong();
        if (loginId == null || loginId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User currentUser = this.getById(loginId);
        if (currentUser == null) {
            StpKit.SPACE.logout();
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 添加用户
     *
     * @param user
     * @return
     */
    @Override
    public long addUser(User user) {
        // 默认密码为 12345678
        String defaultPassword = "12345678";
        String encryptPassword = DigestUtil.md5Hex(defaultPassword + UserConstant.SALT);
        user.setUserPassword(encryptPassword);
        boolean result = this.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return user.getId();
    }

    @Override
    public boolean disableUser(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        Boolean result = transactionTemplate.execute(status -> {
            User locked = baseMapper.selectByIdForUpdate(userId);
            ThrowUtils.throwIf(locked == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            ThrowUtils.throwIf(spaceMapper.selectCount(new QueryWrapper<com.spt.sptpicturebackend.model.entity.Space>()
                    .eq("userId", userId)) > 0,
                    ErrorCode.OPERATION_ERROR, "用户仍拥有有效空间，不能禁用");
            ThrowUtils.throwIf(pictureMapper.countActiveByUserId(userId) > 0,
                    ErrorCode.OPERATION_ERROR, "用户仍拥有有效图片，不能禁用");
            spaceUserMapper.delete(new QueryWrapper<com.spt.sptpicturebackend.model.entity.SpaceUser>()
                    .eq("userId", userId));
            return baseMapper.deleteById(userId) > 0;
        });
        if (Boolean.TRUE.equals(result)) {
            StpKit.SPACE.logout(userId);
        }
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取查询包装类
     *
     * @param userQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortFiled = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortFiled), "ascend".equals(sortOrder), sortFiled);
        return queryWrapper;
    }

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }
}




