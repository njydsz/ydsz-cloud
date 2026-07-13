package com.njydsz.pmis.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.entity.FileTag;
import com.njydsz.pmis.nextwiki.domain.entity.Tag;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.repository.TagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 标签领域服务
 * <p>
 * 管理标签的创建、绑定、解绑、推荐。
 * 标签用于知识库文件的分类和检索，支持手动创建和基于文件名的自动推荐。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagDomainService {

    private final TagRepository tagRepository;
    private final FileNodeRepository fileNodeRepository;

    /**
     * 创建标签
     */
    public Tag createTag(String name, String color, String userId) {
        if (name == null || name.trim().isEmpty()) {
            throw BusinessException.builder().key("标签名称不能为空").build();
        }

        Tag existing = tagRepository.findByName(name.trim());
        if (existing != null) {
            throw BusinessException.builder().key("标签名称已存在: " + name).build();
        }

        Tag tag = Tag.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .name(name.trim())
                .color(color != null ? color : "#1890ff")
                .type("manual")
                .usageCount(0)
                .revision(0)
                .deleted(0)
                .build();

        tag.setCreatedBy(userId);
        tag.setCreatedAt(LocalDateTime.now());
        tag.setUpdatedBy(userId);
        tag.setUpdatedAt(LocalDateTime.now());

        Tag saved = tagRepository.save(tag);
        log.info("[TagDomainService] 创建标签: name={}, userId={}", name, userId);
        return saved;
    }

    /**
     * 查询所有标签
     */
    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    /**
     * 查询文件的标签列表
     */
    public List<Tag> getFileTags(String fileNodeId) {
        return tagRepository.findByFileNodeId(fileNodeId);
    }

    /**
     * 批量绑定标签到文件
     */
    public void batchBindTags(String fileNodeId, List<String> tagIds, String userId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null) {
            throw BusinessException.builder().key("文件节点不存在: " + fileNodeId).build();
        }

        for (String tagId : tagIds) {
            Tag tag = tagRepository.findById(tagId);
            if (tag == null) {
                log.warn("[TagDomainService] 标签不存在，跳过: tagId={}", tagId);
                continue;
            }

            List<FileTag> existing = tagRepository.findFileTagsByFileNodeId(fileNodeId);
            boolean alreadyBound = existing.stream().anyMatch(ft -> tagId.equals(ft.getTagId()));
            if (alreadyBound) {
                continue;
            }

            tagRepository.bindTag(fileNodeId, tagId);
            tagRepository.incrementUsage(tagId);
        }

        log.info("[TagDomainService] 批量绑定标签: fileNodeId={}, tagCount={}", fileNodeId, tagIds.size());
    }

    /**
     * 解绑标签
     */
    public void unbindTag(String fileNodeId, String tagId) {
        tagRepository.unbindTag(fileNodeId, tagId);
        tagRepository.decrementUsage(tagId);
        log.info("[TagDomainService] 解绑标签: fileNodeId={}, tagId={}", fileNodeId, tagId);
    }

    /**
     * 推荐标签（基于文件名和后缀）
     */
    public List<Tag> recommendTags(String fileNodeId) {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null) {
            return List.of();
        }

        List<Tag> allTags = tagRepository.findAll();
        List<Tag> recommended = new ArrayList<>();

        String fileName = fileNode.getName() != null ? fileNode.getName().toLowerCase() : "";
        String suffix = fileNode.getSuffix() != null ? fileNode.getSuffix().toLowerCase() : "";

        for (Tag tag : allTags) {
            String tagName = tag.getName() != null ? tag.getName().toLowerCase() : "";
            if (fileName.contains(tagName) || tagName.contains(suffix)) {
                recommended.add(tag);
            }
        }

        if (recommended.size() < 5) {
            List<Tag> existing = tagRepository.findByFileNodeId(fileNodeId);
            List<String> existingIds = existing.stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());

            for (Tag tag : allTags) {
                if (recommended.size() >= 5) {
                    break;
                }
                if (!existingIds.contains(tag.getId()) && !recommended.contains(tag)) {
                    if (tag.getUsageCount() != null && tag.getUsageCount() > 0) {
                        recommended.add(tag);
                    }
                }
            }
        }

        log.info("[TagDomainService] 推荐标签: fileNodeId={}, count={}", fileNodeId, recommended.size());
        return recommended;
    }
}
