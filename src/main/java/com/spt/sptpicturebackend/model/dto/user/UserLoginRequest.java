package com.spt.sptpicturebackend.model.dto.user;

import lombok.Data;

/**
 * @author zhiyuan
 */
@Data
public class UserLoginRequest {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;
}
