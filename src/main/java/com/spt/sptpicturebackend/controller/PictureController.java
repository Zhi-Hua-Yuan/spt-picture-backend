package com.spt.sptpicturebackend.controller;

import com.spt.sptpicturebackend.annotation.AuthCheck;
import com.spt.sptpicturebackend.common.BaseResponse;
import com.spt.sptpicturebackend.common.ResultUtils;
import com.spt.sptpicturebackend.constant.UserConstant;
import com.spt.sptpicturebackend.model.dto.picture.PictureUploadRequest;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.picture.PictureVO;
import com.spt.sptpicturebackend.service.FileService;
import com.spt.sptpicturebackend.service.PictureService;
import com.spt.sptpicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author zhiyuan
 */
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param request
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart(value = "file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request
    ) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

}





