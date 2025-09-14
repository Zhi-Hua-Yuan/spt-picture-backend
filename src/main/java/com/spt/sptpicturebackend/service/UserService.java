package com.spt.sptpicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.LoginUserVO;
import com.spt.sptpicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author zhiyuan
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2025-09-13 16:55:07
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     *用户登录
     *
     * @param userAccount
     * @param userPassword
     * @param request
     * @return 脱敏后的用户信息
     */
    UserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获得当前登录用户的脱敏信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取当前登录用户的详细信息
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);
}
