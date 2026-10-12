package com.spt.sptpicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.spt.sptpicturebackend.annotation.AuthCheck;
import com.spt.sptpicturebackend.api.aliyunai.AliYunAiApi;
import com.spt.sptpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.spt.sptpicturebackend.api.aliyunai.model.GetOutPaintingTaskResultResponse;
import com.spt.sptpicturebackend.common.BaseResponse;
import com.spt.sptpicturebackend.common.DeleteRequest;
import com.spt.sptpicturebackend.common.ResultUtils;
import com.spt.sptpicturebackend.constant.UserConstant;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.manager.auth.SpaceUserAuthManager;
import com.spt.sptpicturebackend.manager.auth.StpKit;
import com.spt.sptpicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.spt.sptpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.spt.sptpicturebackend.model.dto.picture.*;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.Space;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.enums.PictureReviewStatusEnum;
import com.spt.sptpicturebackend.model.vo.picture.PictureTagCategory;
import com.spt.sptpicturebackend.model.vo.picture.PictureSearchFacetVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureSearchPageVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureTaskVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureDlqReplayResultVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureVO;
import com.spt.sptpicturebackend.search.PictureSearchService;
import com.spt.sptpicturebackend.search.PictureSearchTaskService;
import com.spt.sptpicturebackend.service.PictureService;
import com.spt.sptpicturebackend.service.PictureAsyncProcessingService;
import com.spt.sptpicturebackend.service.PictureDlqReplayService;
import com.spt.sptpicturebackend.service.SpaceService;
import com.spt.sptpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private SpaceService spaceService;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private PictureSearchService pictureSearchService;

    @Resource
    private PictureSearchTaskService pictureSearchTaskService;

    @Resource
    private PictureAsyncProcessingService pictureAsyncProcessingService;

    @Resource
    private PictureDlqReplayService pictureDlqReplayService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            // 初始缓存数目的容量
            .initialCapacity(1024)
            // 最多缓存 10000 条
            .maximumSize(10000L)
            // 设置 5 分钟后过期
            .expireAfterWrite(5L, TimeUnit.MINUTES).build();

    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart(value = "file") MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        pictureService.deletePicture(id, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片
     *
     * @param pictureUpdateRequest
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        pictureService.updatePicture(pictureUpdateRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 获取图片列表【管理员】
     *
     * @param pictureQueryRequest
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page")
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        Page<Picture> page = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(page);
    }

    /**
     * 获取图片列表（需要脱敏）
     *
     * @param pictureQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        // 公开图库
        if (spaceId == null) {
            // 普通用户默认只能查看已过审的公开数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
//            User loginUser = userService.getLoginUser(request);
//            Space space = spaceService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            boolean hasSpacePermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasSpacePermission, ErrorCode.NO_AUTH_ERROR, "没有空间权限");
        }
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 获取图片列表（需要脱敏）（缓存）
     *
     * @param pictureQueryRequest
     * @return
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 可以保存空值，避免缓存穿透
        // 查询本地缓存，构建查询条件
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "sptpicture:listPictureVOByPage:" + hashKey;

        String localValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (localValue != null) {
            // 若本地缓存中有，则直接从缓存中取
            Page<PictureVO> cachedPage = JSONUtil.toBean(localValue, Page.class);
            return ResultUtils.success(cachedPage);
        }
        // 若本地缓存中没有，则查询 redis 缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String redisValue = valueOps.get(cacheKey);
        // 如果 redis 缓存中存在，则将内容存到本地缓存，并直接返回结果
        if (redisValue != null) {
            Page<PictureVO> cachedPage = JSONUtil.toBean(redisValue, Page.class);
            LOCAL_CACHE.put(cacheKey, JSONUtil.toJsonStr(cachedPage));
            return ResultUtils.success(cachedPage);
        }
        // 如果 redis 缓存不存在，则尝试获取分布式锁来重建缓存
        String lockKey = "lock:" + cacheKey;
        boolean isLocked = false;
        try {
            // 尝试获取锁，设置一个较短的过期时间，防止死锁
            isLocked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS));
            if (isLocked) {
                // 成功获取锁，查询数据库
                Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
                Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
                String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
                // 存入 redis 和 本地缓存
                int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
                valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
                LOCAL_CACHE.put(cacheKey, cacheValue);
                return ResultUtils.success(pictureVOPage);
            } else {
                // 获取锁失败，说明有其他线程正在重建缓存。
                // 此时可以等待一小段时间，然后重试从缓存获取，或者直接返回一个空页/旧数据。
                // 这里选择等待后重试，是一种简单的策略。
                Thread.sleep(50); // 等待50毫秒
                String retryValue = valueOps.get(cacheKey);
                if (retryValue != null) {
                    Page<PictureVO> cachedPage = JSONUtil.toBean(retryValue, Page.class);
                    LOCAL_CACHE.put(cacheKey, retryValue); // 更新本地缓存
                    return ResultUtils.success(cachedPage);
                }
                // 如果重试后缓存还是没有，可能是第一个线程重建失败了，可以返回一个空页或错误。
                return ResultUtils.success(new Page<>());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            if (isLocked) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }

    /**
     * 根据 id 获取图片【管理员】
     *
     * @param id
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/get")
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片脱敏信息【普通用户】
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间的图片，需要校验权限
        Space space = null;
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 获取权限列表
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        // 获取封装类
        return ResultUtils.success(pictureVO);
    }


    /**
     * 编辑（用户）
     *
     * @param pictureEditRequest
     * @return
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 获取标签和分类
     *
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 图片审核【管理员】
     *
     * @param pictureReviewRequest
     * @param request
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/review")
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 图片批量上传【管理员】
     *
     * @param pictureUploadByBatchRequest
     * @param request
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/upload/batch")
    @Deprecated
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }

    /** Elasticsearch-backed search with MySQL fallback. */
    @PostMapping("/search/page")
    public BaseResponse<PictureSearchPageVO> searchPicturePage(@RequestBody PictureSearchRequest searchRequest,
                                                                HttpServletRequest request) {
        return ResultUtils.success(pictureSearchService.search(searchRequest, request));
    }

    @GetMapping("/search/suggestions")
    public BaseResponse<List<String>> searchPictureSuggestions(String keyword, Long spaceId, Integer limit,
                                                                HttpServletRequest request) {
        return ResultUtils.success(pictureSearchService.suggestions(keyword, spaceId, limit, request));
    }

    @GetMapping("/search/facets")
    public BaseResponse<PictureSearchFacetVO> searchPictureFacets(Long spaceId, HttpServletRequest request) {
        return ResultUtils.success(pictureSearchService.facets(spaceId, request));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/search/index/rebuild")
    public BaseResponse<Long> rebuildPictureSearchIndex(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureSearchTaskService.submitRebuild(loginUser));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/search/index/reconcile")
    public BaseResponse<Long> reconcilePictureSearchIndex(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureSearchTaskService.submitReconcile(loginUser));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/mq/dlq/replay")
    public BaseResponse<PictureDlqReplayResultVO> replayPictureDlq(
            @RequestBody PictureDlqReplayRequest request) {
        return ResultUtils.success(pictureDlqReplayService.replay(request));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/batch/tasks")
    public BaseResponse<PictureTaskVO> createPictureBatchTask(@RequestBody PictureBatchTaskRequest request,
                                                                HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(pictureAsyncProcessingService.submitBatch(request, loginUser));
    }

    @GetMapping("/tasks/{taskId}")
    public BaseResponse<PictureTaskVO> getPictureTask(@PathVariable Long taskId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureAsyncProcessingService.getTask(taskId, loginUser));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public BaseResponse<Boolean> retryPictureTask(@PathVariable Long taskId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        pictureAsyncProcessingService.retry(taskId, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 图片批量编辑
     *
     * @param pictureEditByBatchRequest
     * @param request
     * @return
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建 AI 扩图任务
     */
    @PostMapping("/out_painting/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(@RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResultResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResultResponse task = aliYunAiApi.getOutPaintingTaskResult(taskId);
        return ResultUtils.success(task);
    }

}





