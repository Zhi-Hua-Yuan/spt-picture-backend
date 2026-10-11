package com.spt.sptpicturebackend.constant;

/**
 * @author zhiyuan
 */
public interface UserConstant {

    /**
     * 用户密码加密盐值
     */
    String SALT = "spt";

    /**
     * 默认权限 普通用户
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员权限
     */
    String ADMIN_ROLE = "admin";
}
