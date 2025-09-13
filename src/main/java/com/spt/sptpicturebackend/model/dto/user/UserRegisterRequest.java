package com.spt.sptpicturebackend.model.dto.user;

import lombok.Data;

/**
 * @author zhiyuan
 */
@Data
public class UserRegisterRequest {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;

    /**
     * 校验密码
     */
    private String checkPassword;
}
