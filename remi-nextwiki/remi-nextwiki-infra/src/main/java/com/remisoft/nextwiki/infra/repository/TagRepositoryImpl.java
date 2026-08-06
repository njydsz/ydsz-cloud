package com.remisoft.nextwiki.infra.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.remisoft.nextwiki.domain.entity.FileTag;
import com.remisoft.nextwiki.domain.entity.Tag;
import com.remisoft.nextwiki.domain.repository.TagRepository;
import com.remisoft.nextwiki.infra.mapper.TagMapper;

import lombok.RequiredArgsConstructor;

/**
 * 标签仓储实现
 *
 * @author remi-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final TagMapper tagMapper;

    /**
     * 插入新标签记录（首次创建标签时调用）。
     *
     * @param tag 待持久化的标签实体（含 name、color 等）
     * @return 已落库的标签实体（含自增主键）
     */
    @Override
    public Tag save(Tag tag) {
        tagMapper.insert(tag);
        return tag;
    }

    /**
     * 按主键查询标签。
     *
     * @param id 标签主键
     * @return 标签实体；不存在则返回 null
     */
    @Override
    public Tag findById(String id) {
        return tagMapper.selectById(id);
    }

    /**
     * 按标签名查询（命中 uk_tag_name 唯一索引）；租户隔离由 MyBatis 拦截器自动注入。
     *
     * @param name 标签名称
     * @return 命中的标签实体；不存在则返回 null
     */
    @Override
    public Tag findByName(String name) {
        return tagMapper.selectByName(name);
    }

    /**
     * 查询当前租户下的全部标签（用于标签选择器/管理列表）。
     *
     * @return 标签列表
     */
    @Override
    public List<Tag> findAll() {
        return tagMapper.selectAll();
    }

    /**
     * 查询指定文件节点已绑定的全部标签。
     *
     * @param fileNodeId 文件节点 ID
     * @return 标签列表
     */
    @Override
    public List<Tag> findByFileNodeId(String fileNodeId) {
        return tagMapper.selectByFileNodeId(fileNodeId);
    }

    /**
     * 将标签绑定到文件：构建文件-标签关联记录（生成 UUID 主键、revision=0、deleted=0）并插入，
     * 绑定成功后由调用方负责标签 usage_count 自增。
     *
     * @param fileNodeId 文件节点 ID
     * @param tagId 标签 ID
     */
    @Override
    public void bindTag(String fileNodeId, String tagId) {
        FileTag fileTag = FileTag.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
                .fileNodeId(fileNodeId)
                .tagId(tagId)
                .revision(0)
                .deleted(0)
                .build();
        tagMapper.insertFileTag(fileTag);
    }

    /**
     * 解除单条文件-标签绑定关系（解绑后由调用方负责 usage_count 自减）。
     *
     * @param fileNodeId 文件节点 ID
     * @param tagId 标签 ID
     */
    @Override
    public void unbindTag(String fileNodeId, String tagId) {
        tagMapper.deleteFileTag(fileNodeId, tagId);
    }

    /**
     * 解除指定文件节点的全部标签绑定（文件删除/移出回收站时级联清理关联）。
     *
     * @param fileNodeId 文件节点 ID
     */
    @Override
    public void unbindAllByFileNodeId(String fileNodeId) {
        tagMapper.deleteAllFileTags(fileNodeId);
    }

    /**
     * 查询指定文件节点的全部文件-标签关联记录（用于展示已绑定关系）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 文件-标签关联列表
     */
    @Override
    public List<FileTag> findFileTagsByFileNodeId(String fileNodeId) {
        return tagMapper.selectFileTagsByFileNodeId(fileNodeId);
    }

    /**
     * 标签使用计数 +1（绑定标签时调用），反映标签热度。
     *
     * @param tagId 标签 ID
     */
    @Override
    public void incrementUsage(String tagId) {
        tagMapper.incrementUsage(tagId);
    }

    /**
     * 标签使用计数 -1（解绑标签时调用）；SQL 使用 GREATEST(usage_count - 1, 0) 防止计数出现负数。
     *
     * @param tagId 标签 ID
     */
    @Override
    public void decrementUsage(String tagId) {
        tagMapper.decrementUsage(tagId);
    }

    /**
     * 按标签名模糊匹配，返回关联的文件节点 ID 列表，用于按标签批量检索/聚合文件。
     *
     * @param tagName 标签名模糊关键字（LIKE %tagName%）
     * @return 命中标签所关联的文件节点 ID 列表
     */
    @Override
    public List<String> findFileNodeIdsByTagName(String tagName) {
        return tagMapper.findFileNodeIdsByTagName(tagName);
    }
}
