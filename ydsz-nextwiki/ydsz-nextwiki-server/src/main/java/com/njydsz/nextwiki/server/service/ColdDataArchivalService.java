package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.config.NextwikiProperties;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;

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
  private final IFileStorageProvider fileStorageProvider;

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
    List<FileNodeVO> coldCandidates =
        fileNodeRepository.findColdCandidates(threshold, config.getExcludeExtensions(), batchSize);

    if (coldCandidates == null || coldCandidates.isEmpty()) {
      log.info("[ColdDataArchival] 无冷数据需归档");
      return 0;
    }

    log.info("[ColdDataArchival] 发现冷数据候选: count={}", coldCandidates.size());

    int archivedCount = 0;
    for (FileNodeVO file : coldCandidates) {
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
   * 解冻归档文件（用户访问冷数据时调用）。
   *
   * @param file 文件节点
   * @return 是否成功提交解冻请求
   */
  public boolean restoreFromArchive(FileNodeVO file) {
    if (file.getStorageKey() == null || file.getStorageKey().isEmpty()) {
      log.warn("[ColdDataArchival] 文件无 storageKey，无法解冻: fileNodeId={}", file.getId());
      return false;
    }

    IFileStorage storage = fileStorageProvider.getStorage();
    if (storage == null) {
      log.warn("[ColdDataArchival] 存储实例未初始化，无法解冻: fileNodeId={}", file.getId());
      return false;
    }

    try {
      // 将对象从归档存储解冻回标准存储
      storage.changeStorageClass(file.getBucketName(), file.getStorageKey(), "STANDARD");
      // 更新元数据标记
      file.setStorageClass("STANDARD");
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(file));
      log.info("[ColdDataArchival] 解冻请求已提交: fileNodeId={}, name={}", file.getId(), file.getName());
      return true;
    } catch (UnsupportedOperationException e) {
      log.warn("[ColdDataArchival] 当前存储后端不支持解冻: fileNodeId={}", file.getId());
      return false;
    } catch (Exception e) {
      log.error("[ColdDataArchival] 解冻失败: fileNodeId={}, error={}", file.getId(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * 归档单个文件。
   *
   * @param file 文件节点
   * @param storageClass 目标存储类型
   */
  private void archiveFile(FileNodeVO file, String storageClass) {
    if (file.getStorageKey() == null || file.getStorageKey().isEmpty()) {
      log.warn("[ColdDataArchival] 文件无 storageKey，跳过归档: fileNodeId={}", file.getId());
      return;
    }

    IFileStorage storage = fileStorageProvider.getStorage();
    if (storage == null) {
      log.warn("[ColdDataArchival] 存储实例未初始化，跳过归档: fileNodeId={}", file.getId());
      return;
    }

    try {
      // 调用存储 API 变更存储类型
      storage.changeStorageClass(file.getBucketName(), file.getStorageKey(), storageClass);
      // 标记文件为已归档
      file.setStorageClass(storageClass);
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(file));
      log.info("[ColdDataArchival] 归档文件: fileNodeId={}, name={}, -> {}", file.getId(),
          file.getName(), storageClass);
    } catch (UnsupportedOperationException e) {
      log.warn("[ColdDataArchival] 当前存储后端不支持存储类型变更: fileNodeId={}", file.getId());
    } catch (Exception e) {
      log.error("[ColdDataArchival] 归档失败: fileNodeId={}, error={}", file.getId(), e.getMessage(), e);
      throw e;
    }
  }
}
