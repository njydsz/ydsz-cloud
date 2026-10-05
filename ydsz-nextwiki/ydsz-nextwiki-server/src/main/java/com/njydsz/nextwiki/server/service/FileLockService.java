package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

/**
 * 文件锁定/解锁应用服务。
 *
 * <p>封装网盘文件的 Check-out / Check-in 机制，防止多用户并发编辑冲突。
 * 仅承担状态字段的更新职责；权限校验由调用方（Controller）通过
 * {@link FilePermissionService} 完成。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileLockService {

  private final FileNodeRepository fileNodeRepository;
  private final NextwikiStructMapper mapper;

  /**
   * 锁定文件（Check-out）。
   *
   * <p>将文件状态置为 {@code locked}，并记录锁定人。已锁定且非本人锁定的文件再次锁定会被拒绝。
   *
   * @param nodeId 文件节点 ID
   * @param userId 当前用户 ID（锁定人）
   * @throws BusinessException 文件不存在/非文件（FILE_NOT_FOUND）或已被他人锁定（FILE_LOCKED）
   */
  @Transactional(rollbackFor = Exception.class)
  public void lock(String nodeId, String userId) {
    FileNodeVO node = fileNodeRepository.findById(nodeId).orElse(null);
    if (node == null || !node.isFile()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }

    if ("locked".equals(node.getStatus()) && !userId.equals(node.getUpdatedBy())) {
      throw new BusinessException(NextwikiExceptionCode.FILE_LOCKED);
    }

    node.setStatus("locked");
    node.setUpdatedBy(userId);
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(mapper.fileNodeVOToDTO(node));

    log.info("[FileLockService] 锁定文件: nodeId={}, userId={}", nodeId, userId);
  }

  /**
   * 解锁文件（Check-in）。
   *
   * <p>将文件状态从 {@code locked} 恢复为 {@code active}，释放编辑权。
   *
   * @param nodeId 文件节点 ID
   * @param userId 当前用户 ID（解锁人）
   * @throws BusinessException 文件不存在（FILE_NOT_FOUND）
   */
  @Transactional(rollbackFor = Exception.class)
  public void unlock(String nodeId, String userId) {
    FileNodeVO node = fileNodeRepository.findById(nodeId).orElse(null);
    if (node == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }

    node.setStatus("active");
    node.setUpdatedBy(userId);
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(mapper.fileNodeVOToDTO(node));

    log.info("[FileLockService] 解锁文件: nodeId={}, userId={}", nodeId, userId);
  }
}
