package com.spt.sptpicturebackend.service;

import com.spt.sptpicturebackend.model.dto.file.UploadPictureResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author zhiyuan
 */
@Deprecated
public interface FileService {

    /**
     * 上传图片
     *
     * @param multipartFile 文件
     * @param uploadPathPrefix 上传路径前缀
     * @return 上传结果
     */
    UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix);

    /**
     * 通过URL上传图片
     *
     * @param fileUrl 文件URL
     * @param uploadPathPrefix 上传路径前缀
     * @return 上传结果
     */
    UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix);

    /**
     * 校验图片
     *
     * @param multipartFile 文件
     */
    void validPicture(MultipartFile multipartFile);

    /**
     * 校验图片
     *
     * @param fileUrl
     */
    void validPicture(String fileUrl);
}
