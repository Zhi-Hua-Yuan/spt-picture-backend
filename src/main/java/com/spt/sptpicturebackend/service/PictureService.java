package com.spt.sptpicturebackend.service;

import com.spt.sptpicturebackend.model.dto.picture.PictureUploadRequest;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.picture.PictureVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author zhiyuan
 * @description 针对表【picture(图片表)】的数据库操作Service
 * @createDate 2025-09-18 15:00:40
 */
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);
}
