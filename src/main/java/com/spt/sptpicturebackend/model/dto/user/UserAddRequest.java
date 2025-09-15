package com.spt.sptpicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * @author zhiyuan
 */
@Data
public class UserAddRequest implements Serializable {

        /**
         * 用户名称
         */
        private String userName;

        /**
         * 用户账号
         */
        private String userAccount;

        /**
         * 用户头像
         */
        private String userAvatar;

        /**
         * 用户简介
         */
        private String userProfile;

        /**
         * 用户角色：user/admin
         */
        private String userRole;

        private static final long serialVersionUID = 1L;
}
