package com.njydsz.pmis.nextwiki.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.FileNodeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件节点仓储实现
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
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
        List<FileNode> children = fileNodeMapper.selectChildren(parentId);
        return children.size();
    }

    @Override
    public List<FileNode> findByPathPrefix(String pathPrefix) {
        return fileNodeMapper.selectByPathPrefix(pathPrefix);
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
        fileNodeMapper.updateById(node);
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
        root.setCreatedAt(LocalDateTime.now());
        root.setUpdatedBy(userId);
        root.setUpdatedAt(LocalDateTime.now());

        fileNodeMapper.insert(root);
        log.info("[FileNodeRepositoryImpl] 创建用户根目录: userId={}, rootId={}", userId, root.getId());
        return root;
    }
}
