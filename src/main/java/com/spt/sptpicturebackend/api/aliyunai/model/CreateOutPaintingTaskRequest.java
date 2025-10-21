package com.spt.sptpicturebackend.api.aliyunai.model;

import lombok.Data;

/**
 * 创建AI任务请求类
 * 用于调用阿里云百炼AI图像处理接口
 * @author zhiyuan
 */
@Data
public class CreateOutPaintingTaskRequest {

    /**
     * 模型名称，必选
     * 示例值：wanx2.1-imageedit
     */
    private String model = "wanx2.1-imageedit";

    /**
     * 输入参数，必选
     */
    private Input input;

    /**
     * 处理参数，可选
     */
    private Parameters parameters;

    /**
     * 输入参数内部类
     */
    @Data
    public static class Input {
        /**
         * 图像编辑功能，必选
         * 支持：expand(扩图)、stylization_all(全局风格化)等
         */
        private String function = "expand";

        /**
         * 提示词，必选
         * 用来描述生成图像中期望包含的元素和视觉特点
         * 支持中英文，长度不超过800个字符
         */
        private String prompt;

        /**
         * 输入图像的URL或Base64编码数据，必选
         * 图像格式：JPG、JPEG、PNG、BMP、TIFF、WEBP
         * 图像分辨率：宽度和高度范围为[512, 4096]像素
         * 图像大小：不超过10MB
         */
        private String baseImageUrl;

        /**
         * 涂抹区域图像的URL或Base64编码数据，可选
         * 仅当function设置为description_edit_with_mask时必填
         */
        private String maskImageUrl;
    }

    /**
     * 参数配置内部类
     */
    @Data
    public static class Parameters {
        /**
         * 生成图片的数量，可选
         * 取值范围为1~4张，默认为1
         */
        private Integer n;

        /**
         * 随机数种子，可选
         * 用于控制模型生成内容的随机性
         * 取值范围是[0, 2147483647]
         */
        private Integer seed;

        /**
         * 是否添加水印标识，可选
         * false：不添加水印（默认值）
         * true：添加水印
         */
        private Boolean watermark;

        /**
         * 向上扩展比例，可选
         * 仅当function设置为expand时需填写
         * 默认值为1.0，取值范围[1.0, 2.0]
         */
        private Float topScale;

        /**
         * 向下扩展比例，可选
         * 仅当function设置为expand时需填写
         * 默认值为1.0，取值范围[1.0, 2.0]
         */
        private Float bottomScale;

        /**
         * 向左扩展比例，可选
         * 仅当function设置为expand时需填写
         * 默认值为1.0，取值范围[1.0, 2.0]
         */
        private Float leftScale;

        /**
         * 向右扩展比例，可选
         * 仅当function设置为expand时需填写
         * 默认值为1.0，取值范围[1.0, 2.0]
         */
        private Float rightScale;
    }
}

