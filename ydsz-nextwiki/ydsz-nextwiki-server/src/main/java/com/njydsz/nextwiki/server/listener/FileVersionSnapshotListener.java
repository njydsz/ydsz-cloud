package com.njydsz.nextwiki.server.listener;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.event.FileVersionSnapshotEvent;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.FileVersionRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;
import com.njydsz.nextwiki.domain.service.FileVersionmainService;

/**
 * 文件版本快照事件监听器 — 在主写事务提交后异步创建文件版本记录。
 *
 * <p><b>设计意图（云顶编码规范 35.2 版本快照异步化）：</b>
 *
 * <ul>
 *   <li>通过 {@link TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)} 保证仅在主事务<b>成功提交</b>后触发，
 *       避免回滚事务产生垃圾版本记录
 *   <li>主事务不再包含版本记录的 DB 写入，缩短持锁时间，降低上传操作延迟
 *   <li>版本创建异常被隔离捕获，不影响主业务（主事务已提交成功）
 * </ul>
 *
 * <p><b>注意：</b>本监听器执行在新事务中，若版本创建失败，仅日志告警，不回滚已提交的主业务数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FileVersionSnapshotEvent 文件版本快照事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileVersionSnapshotListener {

  /** 文件版本领域服务 */
  private final FileVersionmainService versionDomainService;

  /** 文件节点 Repository */
  private final FileNodeRepository fileNodeRepository;

  /** 文件版本 Repository */
  private final FileVersionRepository versionRepository;

  /**
   * 事务提交后异步创建文件版本记录。
   *
   * <p>异常被隔离捕获，仅日志告警，不回滚已提交的主业务数据（《云顶编码规范》27.3 事件隔离原则）。
   *
   * @param event 文件版本快照事件（含版本创建所需全部参数）
   */
  @Transactional(rollbackFor = Exception.class)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFileVersionSnapshot(FileVersionSnapshotEvent event) {
    try {
      String fileNodeId = event.getFileNodeId();

      // 加载文件节点
      FileNodeVO node = fileNodeRepository.findById(fileNodeId).orElse(null);
      if (node == null) {
        log.error(
            "[FileVersionSnapshotListener] 文件节点不存在，跳过版本创建: fileNodeId={}",
            fileNodeId);
        return;
      }

      // 查询现有版本列表
      List<FileVersionDTO> existingVersionDTOs =
          NextwikiConverter.INSTANT.versionListToDTO(
              versionRepository.findByFileNodeId(fileNodeId));

      // 领域服务构建版本记录
      FileVersionmainService.VersionCreateResult versionResult =
          versionDomainService.createVersion(
              node,
              existingVersionDTOs,
              event.getStorageKey(),
              event.getSize(),
              event.getFileHash(),
              event.getMimeType(),
              event.getRemark(),
              event.getUserId());

      // 持久化版本记录
      versionRepository.setActiveVersion(fileNodeId, -1);
      versionRepository.save(versionResult.newVersion());
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(versionResult.updatedFileNode()));

      // 清理超限旧版本
      cleanupExcessVersions(fileNodeId);

      log.debug(
          "[FileVersionSnapshotListener] 文件版本创建成功: fileNodeId={}, version={}",
          fileNodeId,
          versionResult.newVersion().getVersionNumber());
    } catch (Exception e) {
      // 版本创建失败不回滚主业务（主事务已提交），仅日志告警便于人工补偿
      log.error(
          "[FileVersionSnapshotListener] 文件版本创建失败（需人工补偿）: fileNodeId={}, error={}",
          event.getFileNodeId(),
          e.getMessage(),
          e);
    }
  }

  /**
   * 清理超出保留数量的旧版本。
   *
   * @param fileNodeId 文件节点 ID
   */
  private void cleanupExcessVersions(String fileNodeId) {
    List<FileVersionDTO> allVersionDTOs =
        NextwikiConverter.INSTANT.versionListToDTO(
            versionRepository.findByFileNodeId(fileNodeId));
    List<FileVersionDTO> toDelete = versionDomainService.findVersionsToCleanup(allVersionDTOs);
    for (FileVersionDTO v : toDelete) {
      versionRepository.deleteById(v.getId());
    }
    if (!toDelete.isEmpty()) {
      log.info(
          "[FileVersionSnapshotListener] 批量清理旧版本: fileNodeId={}, deleted={}",
          fileNodeId,
          toDelete.size());
    }
  }
}
