package com.spt.sptpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * @author zhiyuan
 */
@Slf4j
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    @Override
    public void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(fileUrl == null, ErrorCode.PARAMS_ERROR, "图片地址不能为空");
        // 1.校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址格式错误");
        }
        // 2.校验 URL 协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")),
                ErrorCode.PARAMS_ERROR, "仅支持 http 或者 https 协议的文件地址");
        // 3.发送 HEAD 请求以校验文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            if (!response.isOk()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片不存在");
            }
            // 4.校验文件类型
            String contentTye = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentTye)) {
                final List<String> ALLOW_CONTENT_TYPE_LIST = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPE_LIST.contains(contentTye), ErrorCode.PARAMS_ERROR, "文件格式错误");
            }
            // 5.校验文件格式大小
            String contentLength = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLength)) {
                ThrowUtils.throwIf(NumberUtil.parseLong(contentLength) > 1024 * 1024 * 3, ErrorCode.PARAMS_ERROR, "文件大小不能超过3MB");
            }
        } catch (Exception e) {
            log.error("图片校验失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片校验失败");
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public String getOriginalFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    @Override
    public void processFile(Object inputSource, File file) throws IOException {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, file);
    }
}
