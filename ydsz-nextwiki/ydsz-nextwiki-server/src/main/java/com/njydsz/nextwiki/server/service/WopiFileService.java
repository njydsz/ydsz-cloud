package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.Optional;

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
 * WOPI 文件操作服务。
 *
 * <p>封装 WOPI 协议所需的文件查询、锁定/解锁、内容更新等操作。
 * 供 WopiController 调用，避免 Controller 直接依赖 FileNodeRepository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WopiFileService {

  private final FileNodeRepository fileNodeRepository;
  private final NextwikiStructMapper mapper;

  /**
   * 按文件 ID 查询文件节点。
   *
   * @param fileId 文件 ID
   * @return 文件节点 VO
   * @throws BusinessException 文件不存在时抛出 FILE_NOT_FOUND
   */
  public FileNodeVO findFile(String fileId) {
    return fileNodeRepository.findById(fileId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", fileId));
  }

  /**
   * 按文件 ID 查询文件节点（返回 Optional）。
   *
   * @param fileId 文件 ID
   * @return Optional 包装的文件节点 VO
   */
  public Optional<FileNodeVO> findFileOptional(String fileId) {
    return fileNodeRepository.findById(fileId);
  }

  /**
   * 锁定文件（WOPI Lock）。
   *
   * <p>将文件状态置为 {@code locked}，并记录锁定人。
   *
   * @param fileId 文件 ID
   * @param userId 锁定人用户 ID
   * @throws BusinessException 文件不存在时抛出 FILE_NOT_FOUND
   */
  @Transactional(rollbackFor = Exception.class)
  public void lockFile(String fileId, String userId) {
    FileNodeVO node = fileNodeRepository.findById(fileId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", fileId));
    node.setStatus("locked");
    node.setUpdatedBy(userId);
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(mapper.fileNodeVOToDTO(node));
    log.info("[WopiFileService] 锁定文件: fileId={}, userId={}", fileId, userId);
  }

  /**
   * 解锁文件（WOPI Unlock）。
   *
   * <p>将文件状态恢复为 {@code active}。
   *
   * @param fileId 文件 ID
   * @throws BusinessException 文件不存在时抛出 FILE_NOT_FOUND
   */
  @Transactional(rollbackFor = Exception.class)
  public void unlockFile(String fileId) {
    FileNodeVO node = fileNodeRepository.findById(fileId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", fileId));
    node.setStatus("active");
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(mapper.fileNodeVOToDTO(node));
    log.info("[WopiFileService] 解锁文件: fileId={}", fileId);
  }

  /**
   * 更新文件元数据（WOPI PutFile 后更新大小和版本信息）。
   *
   * @param fileId 文件 ID
   * @param userId 操作人用户 ID
   * @param size 新文件大小（字节）
   * @throws BusinessException 文件不存在时抛出 FILE_NOT_FOUND
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateFileMeta(String fileId, String userId, long size) {
    FileNodeVO node = fileNodeRepository.findById(fileId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", fileId));
    node.setSize(size);
    node.setUpdatedBy(userId);
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(mapper.fileNodeVOToDTO(node));
    log.info("[WopiFileService] 更新文件元数据: fileId={}, size={}, userId={}", fileId, size, userId);
  }
}
