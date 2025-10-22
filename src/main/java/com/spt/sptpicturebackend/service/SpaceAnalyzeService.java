package com.spt.sptpicturebackend.service;

import com.spt.sptpicturebackend.model.dto.space.analyze.SpaceCategoryAnalyzeRequest;
import com.spt.sptpicturebackend.model.dto.space.analyze.SpaceSizeAnalyzeRequest;
import com.spt.sptpicturebackend.model.dto.space.analyze.SpaceTagAnalyzeRequest;
import com.spt.sptpicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.space.analyze.SpaceCategoryAnalyzeResponse;
import com.spt.sptpicturebackend.model.vo.space.analyze.SpaceSizeAnalyzeResponse;
import com.spt.sptpicturebackend.model.vo.space.analyze.SpaceTagAnalyzeResponse;
import com.spt.sptpicturebackend.model.vo.space.analyze.SpaceUsageAnalyzeResponse;

import java.util.List;

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

    /**
     * 获取空间分类分析数据
     *
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 获取空间标签分析数据
     *
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取空间大小分析数据
     *
     * @param spaceSizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);
}
