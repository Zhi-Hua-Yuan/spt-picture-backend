package com.spt.sptpicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import cn.hutool.json.JSONUtil;
import com.spt.sptpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.spt.sptpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.spt.sptpicturebackend.api.aliyunai.model.GetOutPaintingTaskResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼AI扩图API调用工具类
 * 基于HuTool工具类实现HTTP请求
 *
 * @author zhiyuan
 */
@Slf4j
@Component
public class AliYunAiApi {

    /**
     * 阿里云百炼API Key
     */
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    /**
     * 创建扩图任务的API地址
     */
    private static final String CREATE_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    /**
     * 查询任务结果的API地址模板
     */
    private static final String QUERY_TASK_URL_TEMPLATE = "https://dashscope.aliyuncs.com/api/v1/tasks/";

    /**
     * 异步请求头值
     */
    private static final String ASYNC_HEADER_VALUE = "enable";

    /**
     * 授权请求头
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 异步处理请求头
     */
    private static final String ASYNC_HEADER = "X-DashScope-Async";

    /**
     * 内容类型请求头
     */
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    /**
     * 内容类型值
     */
    private static final String CONTENT_TYPE_VALUE = "application/json";

    /**
     * Bearer前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 创建AI扩图任务
     *
     * @param request 创建任务请求参数
     * @return 创建任务响应结果
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest request) {
        // 参数校验
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("阿里云API Key未配置");
        }

        try {
            // 将请求对象转换为JSON字符串
            String requestBody = JSONUtil.toJsonStr(request);

            // 发送POST请求
            HttpResponse response = HttpRequest.post(CREATE_TASK_URL)
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + apiKey)
                    .header(ASYNC_HEADER, ASYNC_HEADER_VALUE)
                    .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_VALUE)
                    .body(requestBody)
                    .timeout(30000) // 设置超时时间30秒
                    .execute();

            // 解析响应结果
            String responseBody = response.body();
            log.info("创建AI扩图任务响应: {}", responseBody);

            // 将响应JSON转换为对象
            return JSONUtil.toBean(responseBody, CreateOutPaintingTaskResponse.class);
        } catch (Exception e) {
            log.error("创建AI扩图任务失败", e);
            throw new RuntimeException("创建AI扩图任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询AI扩图任务结果
     *
     * @param taskId 任务ID
     * @return 任务查询响应结果
     */
    public GetOutPaintingTaskResultResponse getOutPaintingTaskResult(String taskId) {
        // 参数校验
        if (StrUtil.isBlank(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }

        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("阿里云API Key未配置");
        }

        try {
            // 构造查询URL
            String queryUrl = QUERY_TASK_URL_TEMPLATE + taskId;

            // 发送GET请求
            HttpResponse response = HttpRequest.get(queryUrl)
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + apiKey)
                    .timeout(30000) // 设置超时时间30秒
                    .execute();

            // 解析响应结果
            String responseBody = response.body();
            log.info("查询AI扩图任务结果响应: {}", responseBody);

            // 将响应JSON转换为对象
            return JSONUtil.toBean(responseBody, GetOutPaintingTaskResultResponse.class);
        } catch (Exception e) {
            log.error("查询AI扩图任务结果失败, taskId: {}", taskId, e);
            throw new RuntimeException("查询AI扩图任务结果失败: " + e.getMessage(), e);
        }
    }
}
