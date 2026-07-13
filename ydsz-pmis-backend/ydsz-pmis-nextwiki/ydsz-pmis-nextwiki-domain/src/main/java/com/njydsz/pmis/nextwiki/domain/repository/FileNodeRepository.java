package com.njydsz.pmis.nextwiki.domain.repository;

import com.njydsz.pmis.nextwiki.domain.entity.FileNode;

import java.util.List;

/**
 * 文件节点仓储接口
 * <p>
 * 领域层定义接口契约，基础设施层提供 MyBatis-Plus 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface FileNodeRepository {

    /**
     * 根据 ID 查询文件节点
     */
    FileNode findById(String id);

    /**
     * 查询子节点列表
     */
    List<FileNode> findChildren(String parentId);

    /**
     * 查询子节点数量
     */
    int countChildren(String parentId);

    /**
     * 根据路径前缀查询（用于递归操作）
     */
    List<FileNode> findByPathPrefix(String pathPrefix);

    /**
     * 保存文件节点
     */
    FileNode save(FileNode node);

    /**
     * 更新文件节点
     */
    void update(FileNode node);

    /**
     * 逻辑删除（移入回收站）
     */
    void softDelete(String id, String originalPath);

    /**
     * 恢复逻辑删除
     */
    void restore(String id);

    /**
     * 物理删除
     */
    void physicalDelete(String id);

    /**
     * 批量查询
     */
    List<FileNode> findByIds(List<String> ids);

    /**
     * 更新存储用量（移动/删除时更新目录统计）
     */
    void updateSize(String id, Long sizeDelta);

    /**
     * 查询用户根目录
     */
    FileNode findOrCreateRoot(String userId);
}
