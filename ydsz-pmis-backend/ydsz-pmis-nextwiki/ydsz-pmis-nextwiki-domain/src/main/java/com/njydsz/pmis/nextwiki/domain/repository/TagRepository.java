package com.njydsz.pmis.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.pmis.nextwiki.domain.entity.FileTag;
import com.njydsz.pmis.nextwiki.domain.entity.Tag;

/**
 * 标签仓储接口
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface TagRepository {

    Tag save(Tag tag);

    Tag findById(String id);

    Tag findByName(String name);

    List<Tag> findAll();

    List<Tag> findByFileNodeId(String fileNodeId);

    void bindTag(String fileNodeId, String tagId);

    void unbindTag(String fileNodeId, String tagId);

    void unbindAllByFileNodeId(String fileNodeId);

    List<FileTag> findFileTagsByFileNodeId(String fileNodeId);

    void incrementUsage(String tagId);

    void decrementUsage(String tagId);

    /**
     * 按标签名搜索关联的文件节点ID
     */
    List<String> findFileNodeIdsByTagName(String tagName);
}
