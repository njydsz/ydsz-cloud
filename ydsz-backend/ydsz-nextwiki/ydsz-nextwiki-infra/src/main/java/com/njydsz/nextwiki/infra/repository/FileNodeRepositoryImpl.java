package com.njydsz.nextwiki.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.infra.mapper.FileNodeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件节点仓储实现
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileNodeRepositoryImpl implements FileNodeRepository {

    private final FileNodeMapper fileNodeMapper;

    @Override
    public FileNode findById(String id) {
        return fileNodeMapper.selectById(id);
    }

    @Override
    public List<FileNode> findChildren(String parentId) {
        return fileNodeMapper.selectChildren(parentId);
    }

    @Override
    public int countChildren(String parentId) {
        return fileNodeMapper.countChildren(parentId);
    }

    @Override
    public PageResult<FileNode> findPageChildren(String parentId, String nodeType,
                                                  String sortBy, String sortDir,
                                                  int page, int pageSize) {
        Page<FileNode> pageParam = new Page<>(page, pageSize);
        IPage<FileNode> result = fileNodeMapper.selectPageByParentId(
                pageParam, parentId, nodeType, sortBy, sortDir);
        return PageResult.of(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public List<FileNode> findByPathPrefix(String pathPrefix) {
        return fileNodeMapper.selectByPathPrefix(pathPrefix);
    }

    @Override
    public int batchUpdatePathPrefix(String oldPathPrefix, String newPathPrefix,
                                      int levelDelta, String excludeId) {
        return fileNodeMapper.batchUpdatePathPrefix(oldPathPrefix, newPathPrefix,
                levelDelta, excludeId);
    }

    @Override
    public int batchSoftDeleteByPathPrefix(String pathPrefix, String excludeId) {
        return fileNodeMapper.batchSoftDeleteByPathPrefix(pathPrefix, excludeId);
    }

    @Override
    public FileNode save(FileNode node) {
        if (node.getId() == null || node.getId().isEmpty()) {
            node.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        fileNodeMapper.insert(node);
        return node;
    }

    @Override
    public void update(FileNode node) {
        if (node.getRevision() == null) {
            // 兜底：未携带 revision 时退化为普通更新，避免业务阻断
            fileNodeMapper.updateById(node);
            return;
        }
        int affected = fileNodeMapper.updateWithRevision(node);
        if (affected == 0) {
            throw new OptimisticLockingFailureException(
                    "FileNode 乐观锁更新失败，id=" + node.getId()
                            + ", revision=" + node.getRevision());
        }
        // 更新成功后 revision +1，保持内存对象与数据库一致
        node.setRevision(node.getRevision() + 1);
    }

    @Override
    public void softDelete(String id, String originalPath) {
        fileNodeMapper.softDelete(id, originalPath);
    }

    @Override
    public void restore(String id) {
        fileNodeMapper.restore(id);
    }

    @Override
    public void physicalDelete(String id) {
        fileNodeMapper.deleteById(id);
    }

    @Override
    public List<FileNode> findByIds(List<String> ids) {
        return fileNodeMapper.selectBatchIds(ids);
    }

    @Override
    public void updateSize(String id, Long sizeDelta) {
        fileNodeMapper.updateSize(id, sizeDelta);
    }

    @Override
    public List<FileNode> searchByName(String keyword, String createdBy) {
        return fileNodeMapper.searchByName(keyword, createdBy);
    }

    @Override
    public int countByUser(String userId) {
        return fileNodeMapper.countByUser(userId);
    }

    @Override
    public int countFoldersByUser(String userId) {
        return fileNodeMapper.countFoldersByUser(userId);
    }

    @Override
    public long sumSizeByUser(String userId) {
        Long sum = fileNodeMapper.sumSizeByUser(userId);
        return sum != null ? sum : 0L;
    }

    @Override
    public List<FileNode> findTopLargeFilesByUser(String userId, int limit) {
        return fileNodeMapper.findTopLargeFilesByUser(userId, limit);
    }

    @Override
    public List<FileTypeStat> statsBySuffixAndUser(String userId) {
        return fileNodeMapper.statsBySuffixAndUser(userId);
    }

    @Override
    public FileNode findOrCreateRoot(String userId) {
        FileNode root = fileNodeMapper.selectRootByUser(userId);
        if (root != null) {
            return root;
        }

        root = FileNode.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .parentId("0")
                .name("root")
                .nodeType(FileNode.TYPE_FOLDER)
                .size(0L)
                .path("/")
                .level(0)
                .sort(0)
                .currentVersion(0)
                .previewReady(false)
                .starred(false)
                .shareStatus("private")
                .status("active")
                .deleted(0)
                .revision(0)
                .build();

        root.setCreatedBy(userId);
        root.setUpdatedBy(userId);

        fileNodeMapper.insert(root);
        log.info("[FileNodeRepositoryImpl] 创建用户根目录: userId={}, rootId={}", userId, root.getId());
        return root;
    }

    @Override
    public FileNode findByFileHash(String fileHash) {
        if (fileHash == null || fileHash.isEmpty()) {
            return null;
        }
        return fileNodeMapper.findByFileHash(fileHash);
    }

    @Override
    public List<FileNode> findByNameAndParent(String name, String parentId, String createdBy) {
        return fileNodeMapper.findByNameAndParent(name, parentId, createdBy);
    }
}
