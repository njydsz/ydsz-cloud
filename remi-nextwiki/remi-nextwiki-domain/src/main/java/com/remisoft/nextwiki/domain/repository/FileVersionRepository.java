package com.remisoft.nextwiki.domain.repository;

import java.util.List;

import com.remisoft.nextwiki.domain.entity.FileVersion;

/**
 * 文件版本仓储接口
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface FileVersionRepository {

    /**
     * 保存版本记录
     */
    FileVersion save(FileVersion version);

    /**
     * 更新版本记录（带 revision 乐观锁）
     */
    void update(FileVersion version);

    /**
     * 查询文件的版本历史
     */
    List<FileVersion> findByFileNodeId(String fileNodeId);

    /**
     * 查询指定版本
     */
    FileVersion findByFileNodeIdAndVersion(String fileNodeId, Integer versionNumber);

    /**
     * 查询当前活跃版本
     */
    FileVersion findActiveVersion(String fileNodeId);

    /**
     * 设置活跃版本
     */
    void setActiveVersion(String fileNodeId, Integer versionNumber);

    /**
     * 删除版本
     */
    void deleteById(String id);

    /**
     * 批量删除超出保留数量的旧版本（保留最近 keepCount 个版本）
     *
     * @param fileNodeId 文件节点ID
     * @param keepCount  保留的版本数量
     * @return 实际删除的版本数
     */
    int deleteExcessVersions(String fileNodeId, int keepCount);

    /**
     * 统计版本数
     */
    int countByFileNodeId(String fileNodeId);

    /**
     * 查询最旧的版本（用于超限清理）
     */
    List<FileVersion> findOldestVersions(String fileNodeId, int limit);
}
