package com.njydsz.pmis.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.pmis.common.domain.query.PageResult;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;

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
     * 数据库分页查询子节点（支持类型过滤与排序）
     *
     * @param parentId 父目录ID
     * @param nodeType 节点类型过滤（file/folder，null 或 all 表示不过滤）
     * @param sortBy   排序字段：name / size / time
     * @param sortDir  排序方向：asc / desc
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<FileNode> findPageChildren(String parentId, String nodeType,
                                           String sortBy, String sortDir,
                                           int page, int pageSize);

    /**
     * 根据路径前缀查询（用于递归操作）
     */
    List<FileNode> findByPathPrefix(String pathPrefix);

    /**
     * 批量更新路径前缀（用于目录移动/重命名时递归更新子节点路径）
     *
     * @param oldPathPrefix 原路径前缀
     * @param newPathPrefix 新路径前缀
     * @param levelDelta    层级变化量
     * @param excludeId     排除的节点ID（目录自身）
     * @return 受影响行数
     */
    int batchUpdatePathPrefix(String oldPathPrefix, String newPathPrefix,
                              int levelDelta, String excludeId);

    /**
     * 批量逻辑删除路径前缀下的所有节点
     *
     * @param pathPrefix 路径前缀
     * @param excludeId  排除的节点ID（目录自身）
     * @return 受影响行数
     */
    int batchSoftDeleteByPathPrefix(String pathPrefix, String excludeId);

    /**
     * 保存文件节点
     */
    FileNode save(FileNode node);

    /**
     * 更新文件节点（带 revision 乐观锁）
     *
     * @throws org.springframework.dao.OptimisticLockingFailureException 乐观锁冲突时抛出
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
     * 按文件名搜索（LIKE）
     */
    List<FileNode> searchByName(String keyword, String createdBy);

    /**
     * 统计用户文件数量
     */
    int countByUser(String userId);

    /**
     * 查询用户文件总大小
     */
    long sumSizeByUser(String userId);

    /**
     * 查询用户大文件 Top-N
     */
    List<FileNode> findTopLargeFilesByUser(String userId, int limit);

    /**
     * 按后缀统计文件数量和大小
     */
    List<FileTypeStat> statsBySuffixAndUser(String userId);

    /**
     * 查询用户根目录
     */
    FileNode findOrCreateRoot(String userId);

    /**
     * 按文件哈希查询（用于秒传去重）
     */
    FileNode findByFileHash(String fileHash);

    /**
     * 按 createdBy + parentId 查询同名文件（防重复上传）
     */
    List<FileNode> findByNameAndParent(String name, String parentId, String createdBy);

    /**
     * 文件类型统计结果
     */
    record FileTypeStat(String suffix, int fileCount, long totalSize) {
    }
}
