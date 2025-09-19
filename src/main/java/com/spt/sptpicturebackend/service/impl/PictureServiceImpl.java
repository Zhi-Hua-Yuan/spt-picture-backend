package com.spt.sptpicturebackend.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.model.dto.file.UploadPictureResult;
import com.spt.sptpicturebackend.model.dto.picture.PictureUploadRequest;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.picture.PictureVO;
import com.spt.sptpicturebackend.service.FileService;
import com.spt.sptpicturebackend.service.PictureService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Date;

/**
 * @author zhiyuan
 * @description 针对表【picture(图片表)】的数据库操作Service实现
 * @createDate 2025-09-18 15:00:40
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileService fileService;

    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 1.判断是增加图片还是更新图片
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            // 检查数据库的图片是否存在
            Picture picture = this.getById(pictureId);
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        // 2.上传图片
        String userId = loginUser.getId().toString();
        String uploadPathPrefix = String.format("picture/%s", userId);
        UploadPictureResult uploadPictureResult = fileService.uploadPicture(multipartFile, uploadPathPrefix);
        // 3.解析图片
        String picName = uploadPictureResult.getPicName();
        int picWidth = uploadPictureResult.getPicWidth();
        int picHeight = uploadPictureResult.getPicHeight();
        Double picScale = uploadPictureResult.getPicScale();
        String picFormat = uploadPictureResult.getPicFormat();
        Long picSize = uploadPictureResult.getPicSize();
        String url = uploadPictureResult.getUrl();
        Picture picture = new Picture();
        picture.setUrl(url);
        picture.setName(picName);
        picture.setPicSize(picSize);
        picture.setPicWidth(picWidth);
        picture.setPicHeight(picHeight);
        picture.setPicScale(picScale);
        picture.setPicFormat(picFormat);
        picture.setUserId(loginUser.getId());
        // 4.更新或新增图片
        if (pictureId != null) {
            // 如果是更新图片，需要补充 id 和 编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "操作失败");
        // 5.返回图片信息
        return PictureVO.objToVo(picture);
    }

}




