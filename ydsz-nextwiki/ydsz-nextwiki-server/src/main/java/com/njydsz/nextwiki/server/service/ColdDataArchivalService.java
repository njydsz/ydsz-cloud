package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.infra.entity.FileNodeDO;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

/**
 * 冷数据归档服务。
 *
 * <p>扫描长期未访问的文件，将其迁移至低成本归档存储，降低存储成本。
 *
 * <p><b>归档策略：</b>
 *
 * <ul>
 *   <li>冷数据判定：文件 {@code coldDaysThreshold} 天未访问（updated_at 早于阈值）
 *   <li>排除规则：临时文件（tmp/cache）不归档
 *   <li>分批处理：每批 {@code batchSize} 个文件，避免一次性处理过多
 *   <li>存储类型：迁移至 GLACIER/DEEP_ARCHIVE 等低成本存储
 * </ul>
 *
 * <p>后续实现方向：
 *
 * <ul>
 *   <li>对接云厂商归档存储 API（S3 Glacier / 阿里归档 / 华为归档）
 *   <li>解冻请求队列：用户访问归档文件时触发异步解冻
 *   <li>存储成本统计：按月计算冷热存储成本对比
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColdDataArchivalService {

  /** 冷数据默认阈值（天） */
  private static final int DEFAULT_COLD_DAYS = 90;

  /** 默认批次大小 */
  private static final int DEFAULT_BATCH_SIZE = 100;

  private final FileNodeRepository fileNodeRepository;
  private final NextwikiProperties nextwikiProperties;

  /**
   * 扫描并归档冷数据。
   *
   * @return 归档的文件数量
   */
  public int scanAndArchive() {
    NextwikiProperties.ArchivalConfig config = nextwikiProperties.getArchival();

    if (!config.isEnabled()) {
      log.debug("[ColdDataArchival] 归档功能未启用，跳过");
      return 0;
    }

    int coldDays =
        config.getColdDaysThreshold() > 0 ? config.getColdDaysThreshold() : DEFAULT_COLD_DAYS;
    int batchSize = config.getBatchSize() > 0 ? config.getBatchSize() : DEFAULT_BATCH_SIZE;

    LocalDateTime threshold = LocalDateTime.now().minusDays(coldDays);

    // 查询冷数据候选（此处分页查询，实际应用需要游标分页）
    List<FileNodeDO> coldCandidates =
        fileNodeRepository.findColdCandidates(threshold, config.getExcludeExtensions(), batchSize);

    if (coldCandidates == null || coldCandidates.isEmpty()) {
      log.info("[ColdDataArchival] 无冷数据需归档");
      return 0;
    }

    log.info("[ColdDataArchival] 发现冷数据候选: count={}", coldCandidates.size());

    int archivedCount = 0;
    for (FileNodeDO file : coldCandidates) {
      try {
        archiveFile(file, config.getArchiveStorageClass());
        archivedCount++;
      } catch (Exception e) {
        log.warn(
            "[ColdDataArchival] 归档失败: fileNodeId={}, name={}, error={}",
            file.getId(),
            file.getName(),
            e.getMessage());
      }
    }

    log.info(
        "[ColdDataArchival] 归档完成: archived={}, total={}", archivedCount, coldCandidates.size());
    return archivedCount;
  }

  /**
   * 查询冷数据统计。
   *
   * @param days 未访问天数阈值
   * @return 冷数据数量
   */
  public long countColdData(int days) {
    LocalDateTime threshold = LocalDateTime.now().minusDays(days);
    return fileNodeRepository.countColdNodes(threshold);
  }

  /**
   * 归档单个文件。
   *
   * @param file 文件节点
   * @param storageClass 目标存储类型
   */
  private void archiveFile(FileNodeDO file, String storageClass) {
    // TODO: 调用云存储 API 变更存储类型
    // 例如：S3: s3Client.restoreObject() / 阿里: ossClient.setObjectStorageClass()
    log.info(
        "[ColdDataArchival] 归档文件: fileNodeId={}, name={}, -> {}",
        file.getId(),
        file.getName(),
        storageClass);

    // 标记文件为已归档
    file.setStorageClass(storageClass);
    fileNodeRepository.update(file);
  }
}
