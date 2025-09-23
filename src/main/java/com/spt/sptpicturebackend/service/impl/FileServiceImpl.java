package com.spt.sptpicturebackend.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.spt.sptpicturebackend.config.CosClientConfig;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.manager.CosManager;
import com.spt.sptpicturebackend.model.dto.file.UploadPictureResult;
import com.spt.sptpicturebackend.service.FileService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author zhiyuan
 */
@Service
@Data
@Slf4j
@Deprecated
public class FileServiceImpl implements FileService {

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 上传图片
     *
     * @param multipartFile    上传文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */
    @Override
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验图片
        validPicture(multipartFile);
        // 图片上传地址
        String uuid = IdUtil.randomUUID();
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        String filename = String.format("%s_%s.%s", uuid, DateUtil.formatDate(new Date()), suffix);
        String filePath = String.format("/%s/%s", uploadPathPrefix, filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filePath, null);
            multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putObject(filePath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
            uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + filePath);
            return uploadPictureResult;
        } catch (IOException e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            if (file != null) {
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error,filePath = {}", filePath);
                }
            }
        }
    }

    /**
     * 通过URL上传图片
     *
     * @param fileUrl          文件URL
     * @param uploadPathPrefix 上传路径前缀
     * @return 上传结果
     */
    @Override
    public UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
        // 校验图片
//        validPicture(multipartFile);
        validPicture(fileUrl);
        // 图片上传地址
        String uuid = IdUtil.randomUUID();
//        String originalFilename = multipartFile.getOriginalFilename();
        String originalFilename = FileUtil.mainName(fileUrl);
        String suffix = FileUtil.getSuffix(originalFilename);
        String filename = String.format("%s_%s.%s", uuid, DateUtil.formatDate(new Date()), suffix);
        String filePath = String.format("/%s/%s", uploadPathPrefix, filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filePath, null);
//            multipartFile.transferTo(file);
            HttpUtil.downloadFile(fileUrl, file);
            PutObjectResult putObjectResult = cosManager.putObject(filePath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
            uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + filePath);
            return uploadPictureResult;
        } catch (IOException e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            if (file != null) {
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error,filePath = {}", filePath);
                }
            }
        }
    }

    /**
     * 校验图片
     *
     * @param multipartFile
     */
    @Override
    public void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1.校验文件大小
        long fileSize = multipartFile.getSize();
        final long One_MB = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > One_MB * 3, ErrorCode.PARAMS_ERROR, "文件大小不能超过3MB");
        // 2.校验文件后缀
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        // 允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "文件格式错误");
    }

    /**
     * 校验图片
     *
     * @param fileUrl
     */
    @Override
    public void validPicture(String fileUrl) {
        ThrowUtils.throwIf(fileUrl == null, ErrorCode.PARAMS_ERROR, "图片地址不能为空");
        // 1.校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址格式错误");
        }
        // 2.校验 URL 协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || !fileUrl.startsWith("https://")),
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
}
