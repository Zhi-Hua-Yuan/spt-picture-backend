package com.spt.sptpicturebackend.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.spt.sptpicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhiyuan
 */
@Component
public class CosManager {

    @Resource
    public CosClientConfig cosClientConfig;

    @Resource
    public COSClient cosClient;

    /**
     * 上传文件
     *
     * @param key
     * @param file
     * @return
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucketName(), key, file);
        // 对图片进行处理
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 图片压缩（转成 webp 格式）
        PicOperations.Rule compressRule = new PicOperations.Rule();
        String webpKey = FileUtil.mainName(key) + ".webp";
        compressRule.setFileId(webpKey);
        compressRule.setBucket(cosClientConfig.getBucketName());
        compressRule.setRule("imageMogr2/format/webp");
        rules.add(compressRule);
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载文件
     *
     * @param key
     * @return
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucketName(), key);
        return cosClient.getObject(getObjectRequest);
    }
}
