package com.njydsz.nextwiki.server.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.domain.service.TagDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 标签应用服务
 * <p>
 * 编排标签创建、绑定、推荐操作，协调领域服务。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagApplicationService {

    private final TagDomainService tagDomainService;

    @Transactional(rollbackFor = Exception.class)
    public Tag createTag(String name, String color, String userId) {
        return tagDomainService.createTag(name, color, userId);
    }

    public List<Tag> getAllTags() {
        return tagDomainService.getAllTags();
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchBindTags(String fileNodeId, List<String> tagIds, String userId) {
        tagDomainService.batchBindTags(fileNodeId, tagIds, userId);
    }

    public List<Tag> getFileTags(String fileNodeId) {
        return tagDomainService.getFileTags(fileNodeId);
    }

    public List<Tag> recommendTags(String fileNodeId) {
        return tagDomainService.recommendTags(fileNodeId);
    }
}
