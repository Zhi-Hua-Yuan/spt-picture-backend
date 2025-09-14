package com.spt.sptpicturebackend.model.enums;

import lombok.Getter;

/**
 * @author zhiyuan
 */
@Getter
public enum UserRoleEnum {

    USER("普通用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据value获取枚举
     * @param value
     * @return
     */
    public static UserRoleEnum getEnumByValue(String value){
       if (value == null){
           return null;
       }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()){
            if (userRoleEnum.value.equals(value)){
                return userRoleEnum;
            }
        }
        return null;
    }
}
