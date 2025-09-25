package com.spt.sptpicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.spt.sptpicturebackend.config.CosClientConfig;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.manager.CosManager;
import com.spt.sptpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author zhiyuan
 */
@Slf4j
@Service
public abstract class PictureUploadTemplate {

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 模板方法，定义上传流程
     */
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1.校验图片
        validPicture(inputSource);

        // 2.获取图片上传地址
        String uuid = IdUtil.randomUUID();
        String originalFilename = getOriginalFilename(inputSource);
        String suffix = FileUtil.getSuffix(originalFilename);
        String filename = String.format("%s_%s.%s", uuid, DateUtil.formatDate(new Date()), suffix);
        String filePath = String.format("/%s/%s", uploadPathPrefix, filename);

        File file = null;
        try {
            // 3，创建临时文件
            file = File.createTempFile(filePath, null);
            // 处理文件来源（本地或URL）
            processFile(inputSource, file);

            // 4. 上传文件到对象存储
            PutObjectResult putObjectResult = cosManager.putObject(filePath, file);
            // 原图信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 图片处理后的信息
            List<CIObject> objectList = putObjectResult.getCiUploadResult().getProcessResults().getObjectList();
            if (ObjUtil.isNotEmpty(objectList)) {
                CIObject compressedCiObject = objectList.get(0);
                // 缩略图默认等于压缩图
                CIObject thumbnailCiObject = objectList.get(0);
                if (objectList.size() > 1){
                    thumbnailCiObject = objectList.get(1);
                }
                return getUploadPictureResult(originalFilename,compressedCiObject,thumbnailCiObject);
            }
            // 5.封装返回结果
            return getUploadPictureResult(imageInfo, originalFilename, file, filePath);
        } catch (IOException e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            // 6.删除临时文件
            deleteTempFile(file);
        }
    }

    /**
     * 校验输入源（本地文件或URL）
     */
    public abstract void validPicture(Object inputSource);

    /**
     * 获取图片原始文件名
     */
    public abstract String getOriginalFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     */
    public abstract void processFile(Object inputSource, File file) throws IOException;

    private UploadPictureResult getUploadPictureResult(ImageInfo imageInfo, String originalFilename, File file, String filePath) {
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
    }

    private UploadPictureResult getUploadPictureResult(String originalFilename, CIObject compressedCiObject,CIObject thumbnailCiObject) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(Long.valueOf(compressedCiObject.getSize()));
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        return uploadPictureResult;
    }

    /**
     * 删除临时文件
     */
    private void deleteTempFile(File file) {
        if (file != null) {
            boolean delete = file.delete();
            if (!delete) {
                log.error("file delete error,filePath = {}", file.getAbsolutePath());
            }
        }
    }


}
