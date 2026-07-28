package com.njydsz.nextwiki.server.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.domain.service.TagDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 标签应用服务。
 * <p>标签管理、打标、查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class TagApplicationService {

    /** 标签领域服务 */
    private final TagDomainService tagDomainService;

    /**
     * 创建标签。
     *
     * @param name   标签名称
     * @param color  标签颜色
     * @param userId 创建者 ID
     * @return 标签实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Tag createTag(String name, String color, String userId) {
        return tagDomainService.createTag(name, color, userId);
    }

    /**
     * 查询全部标签列表。
     *
     * @return 标签列表
     */
    public List<Tag> getAllTags() {
        return tagDomainService.getAllTags();
    }

    /**
     * 批量绑定标签到文件节点。
     *
     * @param fileNodeId 文件节点 ID
     * @param tagIds     标签 ID 列表
     * @param userId     操作者 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchBindTags(String fileNodeId, List<String> tagIds, String userId) {
        tagDomainService.batchBindTags(fileNodeId, tagIds, userId);
    }

    /**
     * 查询文件节点的标签列表。
     *
     * @param fileNodeId 文件节点 ID
     * @return 标签列表
     */
    public List<Tag> getFileTags(String fileNodeId) {
        return tagDomainService.getFileTags(fileNodeId);
    }

    /**
     * 基于文件内容推荐标签。
     *
     * @param fileNodeId 文件节点 ID
     * @return 推荐标签列表
     */
    public List<Tag> recommendTags(String fileNodeId) {
        return tagDomainService.recommendTags(fileNodeId);
    }
}
