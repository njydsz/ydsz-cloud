package com.njydsz.pmis.nextwiki.infra.repository;

import com.njydsz.pmis.nextwiki.domain.entity.FileTag;
import com.njydsz.pmis.nextwiki.domain.entity.Tag;
import com.njydsz.pmis.nextwiki.domain.repository.TagRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 标签仓储实现
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

    private final TagMapper tagMapper;

    @Override
    public Tag save(Tag tag) {
        tagMapper.insert(tag);
        return tag;
    }

    @Override
    public Tag findById(String id) {
        return tagMapper.selectById(id);
    }

    @Override
    public Tag findByName(String name) {
        return tagMapper.selectByName(name);
    }

    @Override
    public List<Tag> findAll() {
        return tagMapper.selectAll();
    }

    @Override
    public List<Tag> findByFileNodeId(String fileNodeId) {
        return tagMapper.selectByFileNodeId(fileNodeId);
    }

    @Override
    public void bindTag(String fileNodeId, String tagId) {
        FileTag fileTag = new FileTag();
        fileTag.setFileNodeId(fileNodeId);
        fileTag.setTagId(tagId);
        tagMapper.insertFileTag(fileTag);
    }

    @Override
    public void unbindTag(String fileNodeId, String tagId) {
        tagMapper.deleteFileTag(fileNodeId, tagId);
    }

    @Override
    public void unbindAllByFileNodeId(String fileNodeId) {
        tagMapper.deleteAllFileTags(fileNodeId);
    }

    @Override
    public List<FileTag> findFileTagsByFileNodeId(String fileNodeId) {
        return tagMapper.selectFileTagsByFileNodeId(fileNodeId);
    }

    @Override
    public void incrementUsage(String tagId) {
        tagMapper.incrementUsage(tagId);
    }

    @Override
    public void decrementUsage(String tagId) {
        tagMapper.decrementUsage(tagId);
    }
}
