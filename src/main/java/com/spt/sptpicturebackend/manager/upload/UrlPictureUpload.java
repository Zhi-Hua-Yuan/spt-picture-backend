package com.spt.sptpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/** URL uploader with DNS/IP, redirect, content type and streaming-size guards. */
@Slf4j
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public void validPicture(Object inputSource) {
        URI uri = validateUri((String) inputSource);
        HttpResponse<InputStream> response = null;
        try {
            response = send(uri, "HEAD");
            ensureResponse(response);
            ensureContentType(response.headers().firstValue("Content-Type").orElse(null));
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            ThrowUtils.throwIf(contentLength > MAX_BYTES, ErrorCode.PARAMS_ERROR, "文件大小不能超过3MB");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片 URL 校验失败: {}", uri.getHost(), e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址无法访问");
        } finally {
            close(response);
        }
    }

    @Override
    public String getOriginalFilename(Object inputSource) {
        return FileUtil.mainName((String) inputSource);
    }

    @Override
    public void processFile(Object inputSource, File file) throws IOException {
        URI uri = validateUri((String) inputSource);
        HttpResponse<InputStream> response = null;
        try {
            response = send(uri, "GET");
            ensureResponse(response);
            ensureContentType(response.headers().firstValue("Content-Type").orElse(null));
            try (InputStream input = response.body(); OutputStream output = java.nio.file.Files.newOutputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_BYTES) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过3MB");
                    }
                    output.write(buffer, 0, read);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("图片下载被中断", e);
        } finally {
            close(response);
        }
    }

    private URI validateUri(String value) {
        ThrowUtils.throwIf(value == null || value.isBlank(), ErrorCode.PARAMS_ERROR, "图片地址不能为空");
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            ThrowUtils.throwIf(!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme),
                    ErrorCode.PARAMS_ERROR, "仅支持 http 或 https 协议的文件地址");
            ThrowUtils.throwIf(uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null,
                    ErrorCode.PARAMS_ERROR, "图片地址格式错误");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                ThrowUtils.throwIf(address.isAnyLocalAddress() || address.isLoopbackAddress()
                                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                                || address.isMulticastAddress(),
                        ErrorCode.PARAMS_ERROR, "禁止访问内网地址");
            }
            return uri;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址格式错误");
        }
    }

    private HttpResponse<InputStream> send(URI uri, String method) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .header("Accept", "image/*")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private void ensureResponse(HttpResponse<?> response) {
        ThrowUtils.throwIf(response == null || response.statusCode() < 200 || response.statusCode() >= 300,
                ErrorCode.PARAMS_ERROR, "图片地址无法访问或不允许重定向");
    }

    private void ensureContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase();
        ThrowUtils.throwIf(!ALLOWED_CONTENT_TYPES.contains(normalized), ErrorCode.PARAMS_ERROR, "文件格式错误");
    }

    private void close(HttpResponse<InputStream> response) {
        if (response != null && response.body() != null) {
            try {
                response.body().close();
            } catch (IOException ignored) {
            }
        }
    }
}
