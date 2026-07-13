package com.njydsz.pmis.nextwiki.domain.repository;

import com.njydsz.pmis.nextwiki.domain.entity.Tag;
import com.njydsz.pmis.nextwiki.domain.entity.FileTag;

import java.util.List;

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
}
