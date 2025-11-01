package com.spt.sptpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * @author zhiyuan
 */
@Getter
public enum SpaceTypeEnum {

    PRIVATE("私有空间", 0),
    TEAM("团队空间", 1);

    private final String text;

    private final int value;

    SpaceTypeEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据value获取枚举
     */
    public static SpaceTypeEnum getEnumByValue(Integer value){
        if (ObjUtil.isEmpty( value)){
            return null;
        }
        for (SpaceTypeEnum valueEnum : SpaceTypeEnum.values()) {
            if (valueEnum.value == value) {
                return valueEnum;
            }
        }
        return null;
    }
}
