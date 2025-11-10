package com.spt.sptpicturebackend.api.aliyunai.model;

import lombok.Data;
import java.util.List;

/**
 * 查询任务结果响应类
 * 用于接收阿里云百炼AI任务查询接口的响应结果
 * @author zhiyuan
 */
@Data
public class GetOutPaintingTaskResultResponse {

    /**
     * 请求唯一标识，可用于请求明细溯源和问题排查
     */
    private String requestId;

    /**
     * 任务输出信息
     */
    private Output output;

    /**
     * 输出信息统计，只对成功的结果计数
     */
    private Usage usage;

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

        /**
         * 任务提交时间，格式为 YYYY-MM-DD HH:mm:ss.SSS
         */
        private String submitTime;

        /**
         * 任务执行时间，格式为 YYYY-MM-DD HH:mm:ss.SSS
         */
        private String scheduledTime;

        /**
         * 任务完成时间，格式为 YYYY-MM-DD HH:mm:ss.SSS
         */
        private String endTime;

        /**
         * 任务结果列表，包括图像URL、部分任务执行失败报错信息等
         */
        private List<Result> results;

        /**
         * 任务结果统计
         */
        private TaskMetrics taskMetrics;

        /**
         * 输出图像URL地址（任务成功时返回）
         */
        private String outputImageUrl;

        /**
         * 请求失败的错误码，请求成功时不会返回此参数
         */
        private String code;

        /**
         * 请求失败的详细信息，请求成功时不会返回此参数
         */
        private String message;
    }

    /**
     * 任务结果项内部类
     */
    @Data
    public static class Result {

        /**
         * 图像URL，任务成功时返回
         */
        private String url;

        /**
         * 错误码，部分任务失败时返回
         */
        private String code;

        /**
         * 错误信息，部分任务失败时返回
         */
        private String message;
    }

    /**
     * 任务结果统计内部类
     */
    @Data
    public static class TaskMetrics {

        /**
         * 总的任务数
         */
        private Integer total;

        /**
         * 任务状态为成功的任务数
         */
        private Integer succeeded;

        /**
         * 任务状态为失败的任务数
         */
        private Integer failed;
    }

    /**
     * 使用量统计内部类
     */
    @Data
    public static class Usage {

        /**
         * 模型成功生成图片的数量
         */
        private Integer imageCount;
    }
}
