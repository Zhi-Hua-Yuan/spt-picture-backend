package com.spt.sptpicturebackend.api.aliyunai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.spt.sptpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.spt.sptpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.spt.sptpicturebackend.api.aliyunai.model.GetOutPaintingTaskResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 阿里云AI扩图API调用工具类
 * 专门用于处理图像扩图任务的创建和结果查询
 * @author zhiyuan
 */
@Slf4j
@Component
public class AliyunAiImageExpansionClient {

    private static final String CREATE_EXPANSION_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis";
    private static final String QUERY_TASK_RESULT_URL_TEMPLATE = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";
    private static final String MODEL_NAME = "wanx2.1-imageedit";
    private static final String FUNCTION_TYPE = "expand";

    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    /**
     * 创建AI扩图任务（简化参数版本）
     *
     * @param imageUrl 原始图像URL
     * @param prompt 扩图提示词
     * @param parameters 扩展参数对象
     * @return 创建任务响应，包含task_id
     */
    public CreateOutPaintingTaskResponse createExpansionTask(String imageUrl, String prompt,
                                                             CreateOutPaintingTaskRequest.Parameters parameters)  {
        log.info("开始创建阿里云AI扩图任务，图像URL: {}, 提示词: {}", imageUrl, prompt);

        // 构建请求对象
        CreateOutPaintingTaskRequest request = buildExpansionTaskRequest(imageUrl, prompt, parameters);

        String requestBody = JSONUtil.toJsonStr(request);

        HttpResponse response = HttpRequest.post(CREATE_EXPANSION_TASK_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .body(requestBody)
                .execute();

        String responseBody = response.body();
        log.info("创建扩图任务响应: {}", responseBody);

        return JSONUtil.toBean(responseBody, CreateOutPaintingTaskResponse.class);
    }

    /**
     * 创建AI扩图任务（最简版本）
     *
     * @return 创建任务响应，包含task_id
     */
    public CreateOutPaintingTaskResponse createExpansionTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest)  {
        return createExpansionTask(createOutPaintingTaskRequest.getInput().getBaseImageUrl(),createOutPaintingTaskRequest.getInput().getPrompt(),createOutPaintingTaskRequest.getParameters());
    }

    /**
     * 构建扩图任务请求对象
     */
    private CreateOutPaintingTaskRequest buildExpansionTaskRequest(String imageUrl, String prompt,
                                                                   CreateOutPaintingTaskRequest.Parameters parameters) {
        CreateOutPaintingTaskRequest request = new CreateOutPaintingTaskRequest();
        request.setModel(MODEL_NAME);

        // 设置输入参数
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setFunction(FUNCTION_TYPE);
        input.setPrompt(prompt);
        input.setBaseImageUrl(imageUrl);
        request.setInput(input);

        // 设置参数
        if (parameters == null) {
            parameters = new CreateOutPaintingTaskRequest.Parameters();
        }

        // 设置默认值
        if (parameters.getN() == null) {
            parameters.setN(1); // 默认生成1张图片
        }

        request.setParameters(parameters);

        return request;
    }

    /**
     * 根据任务ID查询扩图任务结果
     *
     * @param taskId 任务ID
     * @return 任务结果响应
     */
    public GetOutPaintingTaskResultResponse getExpansionTaskResult(String taskId) {
        log.info("开始查询阿里云AI扩图任务结果，任务ID: {}", taskId);

        String url = String.format(QUERY_TASK_RESULT_URL_TEMPLATE, taskId);

        HttpResponse response = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute();

        String responseBody = response.body();
        log.info("查询扩图任务结果响应: {}", responseBody);

        return JSONUtil.toBean(responseBody, GetOutPaintingTaskResultResponse.class);
    }
}
