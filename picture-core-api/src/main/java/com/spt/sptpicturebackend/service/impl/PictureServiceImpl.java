package com.spt.sptpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.qcloud.cos.model.ciModel.auditing.ImageAuditingResponse;
import com.spt.sptpicturebackend.api.aliyunai.AliYunAiApi;
import com.spt.sptpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.spt.sptpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.manager.CosManager;
import com.spt.sptpicturebackend.manager.upload.FilePictureUpload;
import com.spt.sptpicturebackend.manager.upload.PictureUploadTemplate;
import com.spt.sptpicturebackend.manager.upload.UrlPictureUpload;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.mapper.PictureTaskMapper;
import com.spt.sptpicturebackend.mapper.PictureTaskItemMapper;
import com.spt.sptpicturebackend.mapper.UserMapper;
import com.spt.sptpicturebackend.config.PictureAsyncProperties;
import com.spt.sptpicturebackend.model.dto.file.UploadPictureResult;
import com.spt.sptpicturebackend.model.dto.picture.*;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.PictureTask;
import com.spt.sptpicturebackend.model.entity.PictureTaskItem;
import com.spt.sptpicturebackend.model.entity.Space;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.enums.PictureReviewStatusEnum;
import com.spt.sptpicturebackend.model.vo.picture.PictureVO;
import com.spt.sptpicturebackend.service.PictureService;
import com.spt.sptpicturebackend.service.OutboxEventService;
import com.spt.sptpicturebackend.service.SpaceService;
import com.spt.sptpicturebackend.service.UserService;
import com.spt.sptpicturebackend.utils.ColorSimilarityCalculator;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zhiyuan
 * @description 针对表【picture(图片表)】的数据库操作Service实现
 * @createDate 2025-09-18 15:00:40
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private UserService userService;

    @Lazy
    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private PictureTaskMapper pictureTaskMapper;

    @Resource
    private PictureTaskItemMapper pictureTaskItemMapper;

    @Resource
    private OutboxEventService outboxEventService;

    @Resource
    private PictureAsyncProperties pictureAsyncProperties;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private UserMapper userMapper;

    @Autowired
    private CosManager cosManager;

    /**
     * 上传图片
     *
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        if (pictureUploadRequest.getTaskItemId() != null) {
            PictureTaskItem taskItem = pictureTaskItemMapper.selectById(pictureUploadRequest.getTaskItemId());
            PictureTask task = taskItem == null ? null : pictureTaskMapper.selectById(taskItem.getTaskId());
            ThrowUtils.throwIf(taskItem == null || task == null || !Objects.equals(task.getUserId(), loginUser.getId()),
                    ErrorCode.NO_AUTH_ERROR, "无效的批量任务项");
        }
        // 校验空间是否存在
        // 空间权限校验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 必须空间创建人（管理员）才能上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 最终额度校验在事务内以带上限条件完成，避免并发上传突破额度。
        }
        // 1.判断是增加图片还是更新图片
        Long pictureId = pictureUploadRequest.getId();
        Picture oldPicture = null;
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原有图片一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }

        // 2.上传图片
        String userId = loginUser.getId().toString();
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", userId);
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        String objectKeySeed = null;
        if (pictureUploadRequest.getTaskId() != null && pictureUploadRequest.getTaskItemId() != null
                && StrUtil.isNotBlank(pictureUploadRequest.getEventId())) {
            objectKeySeed = String.format("async/%s/%s/%s", pictureUploadRequest.getTaskId(),
                    pictureUploadRequest.getTaskItemId(), pictureUploadRequest.getEventId());
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix, objectKeySeed);
        // 3.解析图片
        String picName = uploadPictureResult.getPicName();
        int picWidth = uploadPictureResult.getPicWidth();
        int picHeight = uploadPictureResult.getPicHeight();
        Double picScale = uploadPictureResult.getPicScale();
        String picFormat = uploadPictureResult.getPicFormat();
        Long picSize = uploadPictureResult.getPicSize();
        String url = uploadPictureResult.getUrl();
        String thumbnailUrl = uploadPictureResult.getThumbnailUrl();
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setUrl(url);
        picture.setThumbnailUrl(thumbnailUrl);
        picture.setOriginalObjectKey(uploadPictureResult.getOriginalObjectKey());
        picture.setDisplayObjectKey(uploadPictureResult.getDisplayObjectKey());
        picture.setThumbnailObjectKey(uploadPictureResult.getThumbnailObjectKey());
        picture.setPicColor(uploadPictureResult.getPicColor());
        if (StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(picSize);
        picture.setPicWidth(picWidth);
        picture.setPicHeight(picHeight);
        picture.setPicScale(picScale);
        picture.setPicFormat(picFormat);
        picture.setUserId(loginUser.getId());
        // 异步处理开启时只进入待审核状态；关闭时保留原有同步审核行为。
        if (pictureAsyncProperties.isEnabled() && pictureAsyncProperties.isProcessEnabled()) {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
            picture.setReviewerId(null);
            picture.setReviewTime(null);
        } else {
            fillReviewParams(picture, loginUser);
        }
        // 4.更新或新增图片
        if (pictureId != null) {
            // 如果是更新图片，需要补充 id 和 编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
            picture.setVersion(oldPictureVersion(pictureId));
        } else {
            picture.setVersion(1L);
        }
        picture.setProcessStatus(pictureAsyncProperties.isEnabled() && pictureAsyncProperties.isProcessEnabled() ? 0 : 2);
        picture.setTaskItemId(pictureUploadRequest.getTaskItemId());
        // 开启事务
        Long finalSpaceId = spaceId;
        try {
            transactionTemplate.execute(status -> {
                 ThrowUtils.throwIf(userMapper.selectByIdForUpdate(loginUser.getId()) == null,
                         ErrorCode.NOT_LOGIN_ERROR, "用户不存在或已禁用");
                 Picture lockedOldPicture = pictureId == null ? null : baseMapper.selectByIdForUpdate(pictureId);
                 if (pictureId != null) {
                     ThrowUtils.throwIf(lockedOldPicture == null, ErrorCode.NOT_FOUND_ERROR);
                     picture.setVersion((lockedOldPicture.getVersion() == null ? 1L : lockedOldPicture.getVersion()) + 1);
                 }
                 boolean result = this.saveOrUpdate(picture);
                 ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
                 if (pictureAsyncProperties.isEnabled()) {
                     outboxEventService.createPictureChanged(picture.getId(), picture.getVersion());
                 }
                if (finalSpaceId != null) {
                long newSize = picture.getPicSize() == null ? 0L : picture.getPicSize();
                 long oldSize = pictureId == null || lockedOldPicture == null || lockedOldPicture.getPicSize() == null
                        ? 0L : lockedOldPicture.getPicSize();
                long delta = newSize - oldSize;
                String sizeExpression = "GREATEST(totalSize + " + delta + ", 0)";
                String countExpression = pictureId == null ? "totalCount + 1" : "totalCount";
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = " + sizeExpression)
                        .setSql("totalCount = " + countExpression)
                        .apply("totalSize + " + delta + " <= maxSize")
                        .apply(pictureId == null ? "totalCount + 1 <= maxCount" : "totalCount <= maxCount")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                }
                if (pictureAsyncProperties.isEnabled() && pictureAsyncProperties.isProcessEnabled()) {
                PictureTask task = new PictureTask();
                task.setTaskType("PICTURE_PROCESS");
                task.setUserId(loginUser.getId());
                task.setSpaceId(finalSpaceId);
                task.setPictureId(picture.getId());
                task.setStatus("PENDING");
                task.setProgress(0);
                task.setTotalCount(1);
                task.setSuccessCount(0);
                task.setFailedCount(0);
                task.setRetryCount(0);
                task.setCreateTime(new Date());
                task.setUpdateTime(new Date());
                pictureTaskMapper.insert(task);
                picture.setProcessTaskId(task.getId());
                Picture taskUpdate = new Picture();
                taskUpdate.setId(picture.getId());
                taskUpdate.setProcessTaskId(task.getId());
                this.updateById(taskUpdate);
                outboxEventService.createPictureProcessingRequestedV1(picture.getId(), task.getId(), picture.getVersion(), picture.getOriginalObjectKey());
                }
                 if (pictureId != null && lockedOldPicture != null && pictureAsyncProperties.isEnabled() && pictureAsyncProperties.isFileDeleteEnabled()) {
                 Set<String> oldKeys = new LinkedHashSet<>();
                 addIfNotBlank(oldKeys, lockedOldPicture.getOriginalObjectKey());
                 addIfNotBlank(oldKeys, lockedOldPicture.getDisplayObjectKey());
                 addIfNotBlank(oldKeys, lockedOldPicture.getThumbnailObjectKey());
                 if (oldKeys.isEmpty()) {
                     addIfNotBlank(oldKeys, lockedOldPicture.getUrl());
                     addIfNotBlank(oldKeys, lockedOldPicture.getThumbnailUrl());
                }
                    outboxEventService.createFilesDeleteRequestedV1(picture.getId(), picture.getVersion(), new ArrayList<>(oldKeys));
                }
                return picture;
            });
        } catch (RuntimeException error) {
            if (pictureUploadRequest.getTaskItemId() != null) {
                Picture existing = this.lambdaQuery().eq(Picture::getTaskItemId, pictureUploadRequest.getTaskItemId()).one();
                if (existing != null) return PictureVO.objToVo(existing);
            }
            Set<String> keys = new LinkedHashSet<>();
            addIfNotBlank(keys, uploadPictureResult.getOriginalObjectKey());
            addIfNotBlank(keys, uploadPictureResult.getDisplayObjectKey());
            addIfNotBlank(keys, uploadPictureResult.getThumbnailObjectKey());
            try {
                if (!keys.isEmpty()) {
                    outboxEventService.createFilesDeleteRequestedV1(pictureId, picture.getVersion(), new ArrayList<>(keys));
                }
            } catch (Exception cleanupEventError) {
                log.error("图片入库失败且清理事件写入失败", cleanupEventError);
                for (String key : keys) {
                    try { cosManager.deleteObject(key); } catch (Exception ignored) { }
                }
            }
            throw error;
        }

        // 5.返回图片信息
        return PictureVO.objToVo(picture);
    }

    /**
     * 获取查询包装类
     *
     * @param pictureQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String category = pictureQueryRequest.getCategory();
        String introduction = pictureQueryRequest.getIntroduction();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortFiled = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        // 从多字段中查询
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
//        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
//        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(picSize != null && picSize > 0, "picSize", picSize);
        queryWrapper.like(picWidth != null && picWidth > 0, "picWidth", picWidth);
        queryWrapper.like(picHeight != null && picHeight > 0, "picHeight", picHeight);
        queryWrapper.like(picScale != null && picScale > 0, "picScale", picScale);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(userId != null && userId > 0, "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortFiled), "ascend".equals(sortOrder), sortFiled);
        return queryWrapper;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        // 第二种方式，将用户信息放入 set 集合中，查找效率更高
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        // 如果图片列表为空，则直接返回
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserMap = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserMap.containsKey(userId)) {
                user = userIdUserMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
        /* 第一种方式，一个一个查找用户信息，效率低
        // 图片信息脱敏
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        List<PictureVO> pictureVOList = picturePage.getRecords().stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 保存图片里的用户信息
        for (PictureVO pictureVO : pictureVOList){
            Long userId = pictureVO.getUserId();
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
        */

    }

    /**
     * 图片信息校验
     *
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }


    /**
     * 图片信息脱敏
     *
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            pictureVO.setUser(userService.getUserVO(user));
        }
        return pictureVO;
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        updatePicture.setVersion((oldPicture.getVersion() == null ? 1L : oldPicture.getVersion()) + 1);
        transactionTemplate.execute(status -> {
            Picture locked = baseMapper.selectByIdForUpdate(id);
            ThrowUtils.throwIf(locked == null, ErrorCode.NOT_FOUND_ERROR);
            updatePicture.setVersion((locked.getVersion() == null ? 1L : locked.getVersion()) + 1);
            boolean result = this.updateById(updatePicture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            if (pictureAsyncProperties.isEnabled()) {
                outboxEventService.createPictureChanged(id, updatePicture.getVersion());
            }
            return null;
        });
    }

    /**
     * 填充审核信息
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        // 非管理员，创建或编辑都要改为待审核
        picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        // 如果是管理员，则需要经过数据万象审核。
        // 如果审核未通过，设置审核状态为“审核未通过”，并说明理由。
        // 如果审核通过，则设置审核状态为“审核通过”，理由为“经 AI 审核通过”
        if (userService.isAdmin(loginUser)) {
            ImageAuditingResponse response = cosManager.reviewObject(picture.getUrl());
            String result = response.getResult();
            switch (result) {
                case "0":
                    picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
                    picture.setReviewMessage("经 AI 审核通过");
                    break;
                case "1":
                    picture.setReviewStatus(PictureReviewStatusEnum.REJECT.getValue());
                    picture.setReviewMessage("判定为违规敏感文件");
                    break;
                case "2":
                    picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
                    picture.setReviewMessage("疑似敏感，建议人工复核");
            }
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
        }
//        if (userService.isAdmin(loginUser)) {
//            // 管理员自动过审
//            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
//            picture.setReviewerId(loginUser.getId());
//            picture.setReviewMessage("管理员自动过审");
//            picture.setReviewTime(new Date());
//        } else {
//            // 非管理员，创建或编辑都要改为待审核
//            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
//        }
    }

    /**
     * 批量上传图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取图片失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取图片失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取图片失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if (StrUtil.isNotBlank(namePrefix)) {
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 删除图片文件
     *
     * @param oldPicture
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 新数据保留 Object Key，可同时清理原图、展示图和缩略图；旧数据回退到 URL。
        Set<String> keys = new LinkedHashSet<>();
        addIfNotBlank(keys, oldPicture.getOriginalObjectKey());
        addIfNotBlank(keys, oldPicture.getDisplayObjectKey());
        addIfNotBlank(keys, oldPicture.getThumbnailObjectKey());
        if (keys.isEmpty()) {
            Long count = this.lambdaQuery().eq(Picture::getUrl, oldPicture.getUrl()).count();
            if (count != null && count > 1) {
                return;
            }
            addIfNotBlank(keys, oldPicture.getUrl());
            addIfNotBlank(keys, oldPicture.getThumbnailUrl());
        }
        for (String key : keys) {
            cosManager.deleteObject(key);
        }
    }

    @Override
    public void updatePicture(PictureUpdateRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getId() == null || request.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        Picture update = new Picture();
        BeanUtils.copyProperties(request, update);
        update.setTags(JSONUtil.toJsonStr(request.getTags()));
        validPicture(update);
        Picture old = getById(request.getId());
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR);
        fillReviewParams(update, loginUser);
        update.setVersion((old.getVersion() == null ? 1L : old.getVersion()) + 1);
        Boolean result = transactionTemplate.execute(status -> {
            Picture locked = baseMapper.selectByIdForUpdate(request.getId());
            ThrowUtils.throwIf(locked == null, ErrorCode.NOT_FOUND_ERROR);
            update.setVersion((locked.getVersion() == null ? 1L : locked.getVersion()) + 1);
            boolean changed = updateById(update);
            ThrowUtils.throwIf(!changed, ErrorCode.OPERATION_ERROR);
            if (pictureAsyncProperties.isEnabled()) {
                outboxEventService.createPictureChanged(update.getId(), update.getVersion());
            }
            return changed;
        });
        ThrowUtils.throwIf(!Boolean.TRUE.equals(result), ErrorCode.OPERATION_ERROR);
    }

    private void addIfNotBlank(Set<String> values, String value) {
        if (StrUtil.isNotBlank(value)) {
            values.add(value);
        }
    }

    /**
     * 检查图片权限
     *
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 如果是公共图库，则只有本人或者管理员有权限
            if (!userService.isAdmin(loginUser) && !loginUser.getId().equals(picture.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 如果是私有空间，则只有本人有权限
            if (!loginUser.getId().equals(picture.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
//        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            Picture locked = baseMapper.selectByIdForUpdate(pictureId);
            ThrowUtils.throwIf(locked == null, ErrorCode.NOT_FOUND_ERROR);
            BeanUtils.copyProperties(locked, oldPicture);
            // 操作数据库
            boolean result = baseMapper.logicalDeleteAndBumpVersion(pictureId) > 0;
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            oldPicture.setVersion((oldPicture.getVersion() == null ? 1L : oldPicture.getVersion()) + 1);
            oldPicture.setIsDelete(1);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = GREATEST(totalSize - " + (oldPicture.getPicSize() == null ? 0 : oldPicture.getPicSize()) + ", 0)")
                        .setSql("totalCount = GREATEST(totalCount - 1, 0)")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            Set<String> keys = new LinkedHashSet<>();
            addIfNotBlank(keys, oldPicture.getOriginalObjectKey());
            addIfNotBlank(keys, oldPicture.getDisplayObjectKey());
            addIfNotBlank(keys, oldPicture.getThumbnailObjectKey());
            if (keys.isEmpty()) {
                addIfNotBlank(keys, oldPicture.getUrl());
                addIfNotBlank(keys, oldPicture.getThumbnailUrl());
            }
            if (pictureAsyncProperties.isEnabled()) {
                outboxEventService.createPictureDeleted(oldPicture.getId(), oldPicture.getVersion(),
                        Map.of("keys", new ArrayList<>(keys)));
            }
            return true;
        });
        // 异步清理文件；关闭功能开关时保留原有直接清理逻辑。
        if (!pictureAsyncProperties.isEnabled() || !pictureAsyncProperties.isFileDeleteEnabled()) {
            this.clearPictureFile(oldPicture);
        }
    }

    /**
     * 根据颜色搜索图片
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2.校验空间权限
        // 私有空间，只有本人有权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 3.查询该空间所有图片
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有一张图片，则直接返回空列表
        if (ObjUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 4.计算颜色相似度并排序
        Color targetColor = Color.decode(picColor);
        List<Picture> sortedPicture = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                            // 提取图片主色调
                            String hexColor = picture.getPicColor();
                            // 没有主色调的放到最后
                            if (StrUtil.isBlank(hexColor)) {
                                return Double.MAX_VALUE;
                            }
                            Color color = Color.decode(hexColor);
                            // 越大越相似
                            return -ColorSimilarityCalculator.calculateSimilarity(color, targetColor);
                        }
                ))
                // 取前 12 张
                .limit(12)
                .toList();
        // 5.转化为 PictureVO 并返回结果
        return sortedPicture.stream().map(PictureVO::objToVo).toList();
    }

    /**
     * 编辑图片
     *
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        if (!userService.isAdmin(loginUser) && !Objects.equals(oldPicture.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        picture.setVersion((oldPicture.getVersion() == null ? 1L : oldPicture.getVersion()) + 1);
        picture.setProcessStatus(2);
        // 操作数据库
        boolean result = transactionTemplate.execute(status -> {
            Picture locked = baseMapper.selectByIdForUpdate(id);
            ThrowUtils.throwIf(locked == null, ErrorCode.NOT_FOUND_ERROR);
            picture.setVersion((locked.getVersion() == null ? 1L : locked.getVersion()) + 1);
            boolean updated = this.updateById(picture);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
            if (pictureAsyncProperties.isEnabled()) {
                outboxEventService.createPictureChanged(id, picture.getVersion());
            }
            return updated;
        });
        ThrowUtils.throwIf(!Boolean.TRUE.equals(result), ErrorCode.OPERATION_ERROR);
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1.校验参数
        ThrowUtils.throwIf(pictureIdList == null || pictureIdList.isEmpty(), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2.校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 3.查询指定照片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId, Picture::getVersion)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .last("FOR UPDATE")
                .list();
        if (ObjUtil.isEmpty(pictureList)) {
            return;
        }
        // 批量重命名必须在批量更新前完成，保证版本和索引内容一致。
        fillPictureWithNameRule(pictureList, pictureEditByBatchRequest.getNameRule());
        // 4.更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (ObjUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
            picture.setVersion((picture.getVersion() == null ? 1L : picture.getVersion()) + 1);
            picture.setProcessStatus(2);
        });
        // 5.批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        if (pictureAsyncProperties.isEnabled()) {
            pictureList.forEach(picture -> outboxEventService.createPictureChanged(picture.getId(), picture.getVersion()));
        }
    }

    private Long oldPictureVersion(Long pictureId) {
        Picture old = this.getById(pictureId);
        return old == null || old.getVersion() == null ? 1L : old.getVersion() + 1;
    }

    /**
     * 创建 AI 扩图任务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 1.获取图片信息
        Picture picture = this.getById(createPictureOutPaintingTaskRequest.getPictureId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 2.权限校验
//        checkPictureAuth(loginUser, picture);
        // 3.构造 AI 扩图请求参数
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 4.创建任务并返回输出信息
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

}




