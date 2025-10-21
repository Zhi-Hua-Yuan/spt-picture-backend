package com.spt.sptpicturebackend.service;

import com.spt.sptpicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.space.analyze.SpaceUsageAnalyzeResponse;

/**
 * @author zhiyuan
 */
public interface SpaceAnalyzeService {

    /**
     * 获取空间使用情况分析
     *
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);
}
