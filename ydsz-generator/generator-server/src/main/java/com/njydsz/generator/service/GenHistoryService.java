package com.njydsz.generator.service;

import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.repository.GenHistoryFileRepository;
import com.njydsz.generator.repository.GenHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 代码生成历史领域服务（含回滚能力）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenHistoryService {

  private final GenHistoryRepository historyRepository;
  private final GenHistoryFileRepository historyFileRepository;

  /**
   * 查询最近 N 条任务记录。
   *
   * @param limit 数量上限
   * @return 历史记录列表
   */
  public List<GenHistory> listRecent(int limit) {
    return historyRepository.findRecent(Math.max(limit, 1));
  }

  /**
   * 根据 ID 查询任务。
   *
   * @param id 任务 ID
   * @return Optional 任务
   */
  public GenHistory getById(Long id) {
    return historyRepository.findById(id).orElse(null);
  }

  /**
   * 查询任务全部文件明细。
   *
   * @param historyId 任务 ID
   * @return 文件明细列表
   */
  public List<GenHistoryFile> listFiles(Long historyId) {
    return historyFileRepository.findByHistoryId(historyId);
  }

  /**
   * 回滚某次生成任务（恢复原文件 / 删除新生成文件）。
   *
   * <p>回滚策略：
   * <ul>
   *   <li>CREATED → 删除生成的文件</li>
   *   <li>UPDATED → 从备份文件恢复</li>
   *   <li>UNCHANGED → 跳过</li>
   * </ul>
   *
   * @param historyId 任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void rollback(Long historyId) {
    GenHistory history = historyRepository.findById(historyId)
        .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + historyId));
    List<GenHistoryFile> files = historyFileRepository.findByHistoryId(historyId);
    int restored = 0;
    int deleted = 0;

    for (GenHistoryFile file : files) {
      try {
        Path filePath = Paths.get(file.getFilePath());
        switch (file.getAction()) {
          case "CREATED":
            if (Files.exists(filePath)) {
              Files.delete(filePath);
              deleted++;
              log.info("回滚删除文件 {}", file.getFilePath());
            }
            break;
          case "UPDATED":
            if (file.getOriginalBackupPath() != null) {
              Path backupPath = Paths.get(file.getOriginalBackupPath());
              if (Files.exists(backupPath)) {
                Files.copy(backupPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                restored++;
                log.info("回滚恢复文件 {}", file.getFilePath());
              }
            }
            break;
          default:
            // UNCHANGED 跳过
            break;
        }
      } catch (Exception e) {
        log.error("回滚文件失败 path={} err={}", file.getFilePath(), e.getMessage());
      }
    }
    log.info("[ROLLBACK] historyId={} restored={} deleted={}", historyId, restored, deleted);
  }

  /**
   * 清理历史记录（物理删除）。
   *
   * @param id 任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteHistory(Long id) {
    historyFileRepository.deleteByHistoryId(id);
    historyRepository.deleteById(id);
    log.info("删除历史记录 id={}", id);
  }
}
