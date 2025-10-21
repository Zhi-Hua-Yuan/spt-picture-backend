package com.spt.sptpicturebackend.api.aliyunai.model;

import lombok.Data;

/**
 * 创建AI任务响应类
 * 用于接收阿里云百炼AI图像处理接口的响应结果
 * @author zhiyuan
 */
@Data
public class CreateOutPaintingTaskResponse {

    /**
     * 任务输出信息
     */
    private Output output;

    /**
     * 请求唯一标识，可用于请求明细溯源和问题排查
     */
    private String requestId;

    /**
     * 请求失败的错误码，请求成功时不会返回此参数
     */
    private String code;

    /**
     * 请求失败的详细信息，请求成功时不会返回此参数
     */
    private String message;

    /**
     * 任务输出信息内部类
     */
    @Data
    public static class Output {

        /**
         * 任务ID，查询有效期24小时
         */
        private String taskId;

        /**
         * 任务状态
         * PENDING：任务排队中
         * RUNNING：任务处理中
         * SUCCEEDED：任务执行成功
         * FAILED：任务执行失败
         * CANCELED：任务已取消
         * UNKNOWN：任务不存在或状态未知
         */
        private String taskStatus;
    }
}
