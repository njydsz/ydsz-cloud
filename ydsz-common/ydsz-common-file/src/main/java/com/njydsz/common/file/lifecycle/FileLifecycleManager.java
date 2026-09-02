package com.njydsz.common.file.lifecycle;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.file.config.FileLifecycleProperties;
import com.njydsz.common.file.domain.ListObjectsResult;
import com.njydsz.common.file.domain.ObjectMetadata;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.lock.annotation.DistributedScheduled;

/**
 * 文件生命周期管理器
 *
 * <p>支持基于规则的自动过期清理，按文件路径前缀配置不同的保留策略， 定时扫描并清理过期文件。
 *
 * <p><b>推荐规则模板（可在业务模块 yml 中按需选用）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   file:
 *     lifecycle:
 *       enabled: true          # 默认启用（26.09.01+）
 *       cron: "0 0 2 * * ?"    # 每天凌晨 2 点执行
 *       dry-run: false         # 首次启用建议 true 试运行
 *       rules:
 *         - prefix: "temp/"    # 导入/临时缓存 → 1 天
 *           max-age-days: 1
 *         - prefix: "preview/" # 预览缩略图 → 7 天
 *           max-age-days: 7
 *         - prefix: "logs/"    # 导出/日志文件 → 30 天
 *           max-age-days: 30
 *         - prefix: "archive/" # 归档文件 → 365 天
 *           max-age-days: 365
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class FileLifecycleManager {

  private final FileLifecycleProperties lifecycleProperties;
  private final IFileStorageProvider storageProvider;

  /**
   * 定时执行文件清理任务
   *
   * <p>根据配置的 cron 表达式定时触发，遍历所有规则并执行过期文件清理。 通过 {@link DistributedScheduled}
   * 保证多节点部署时仅一个节点执行清理，避免重复删除。
   */
  @Scheduled(cron = "${ydsz.file.lifecycle.cron:0 0 2 * * ?}")
  @DistributedScheduled(lockKey = "file:lifecycle-cleanup", leaseTime = 600)
  public void executeCleanup() {
    if (!lifecycleProperties.isEnabled()) {
      log.debug("文件生命周期清理未启用，跳过执行");
      return;
    }

    List<FileLifecycleProperties.LifecycleRule> rules = lifecycleProperties.getRules();
    if (rules == null || rules.isEmpty()) {
      log.debug("文件生命周期清理规则为空，跳过执行");
      return;
    }

    IFileStorage storage = resolveStorage();
    if (storage == null) {
      log.error("文件生命周期清理失败：无法获取文件存储实例");
      return;
    }

    String bucketName = lifecycleProperties.getBucket();
    boolean dryRun = lifecycleProperties.isDryRun();

    log.info("开始执行文件生命周期清理, ruleCount={}, dryRun={}", rules.size(), dryRun);

    for (FileLifecycleProperties.LifecycleRule rule : rules) {
      try {
        processRule(storage, bucketName, rule, dryRun, new CleanupResult());
      } catch (Exception e) {
        log.error("文件生命周期清理规则执行失败, prefix={}: {}", rule.getPrefix(), e.getMessage(), e);
      }
    }

    log.info("文件生命周期清理执行完成");
  }

  /**
   * 手动触发清理
   *
   * @return 清理结果统计
   */
  public CleanupResult executeManualCleanup() {
    if (!lifecycleProperties.isEnabled()) {
      return CleanupResult.skipped("文件生命周期清理未启用");
    }

    List<FileLifecycleProperties.LifecycleRule> rules = lifecycleProperties.getRules();
    if (rules == null || rules.isEmpty()) {
      return CleanupResult.skipped("文件生命周期清理规则为空");
    }

    IFileStorage storage = resolveStorage();
    if (storage == null) {
      return CleanupResult.failed("无法获取文件存储实例");
    }

    String bucketName = lifecycleProperties.getBucket();
    boolean dryRun = lifecycleProperties.isDryRun();
    CleanupResult result = new CleanupResult();

    for (FileLifecycleProperties.LifecycleRule rule : rules) {
      try {
        processRule(storage, bucketName, rule, dryRun, result);
      } catch (Exception e) {
        log.error("文件生命周期清理规则执行失败, prefix={}: {}", rule.getPrefix(), e.getMessage(), e);
        result.addError(rule.getPrefix(), e.getMessage());
      }
    }

    return result;
  }

  /**
   * 处理单条生命周期规则
   *
   * @param storage 文件存储实例
   * @param bucketName 存储桶名称
   * @param rule 生命周期规则
   * @param dryRun 是否仅模拟执行
   */
  private void processRule(
      IFileStorage storage,
      String bucketName,
      FileLifecycleProperties.LifecycleRule rule,
      boolean dryRun,
      CleanupResult result) {
    String prefix = rule.getPrefix();
    long maxAgeMillis = rule.getMaxAgeDays() * 24L * 60L * 60L * 1000L;
    long cutoffTime = System.currentTimeMillis() - maxAgeMillis;

    log.info("处理生命周期规则: prefix={}, maxAgeDays={}", prefix, rule.getMaxAgeDays());

    String cursor = null;

    while (true) {
      ListObjectsResult listResult = storage.listObjects(bucketName, prefix, cursor, 1000);

      if (listResult == null
          || listResult.getObjects() == null
          || listResult.getObjects().isEmpty()) {
        break;
      }

      for (ObjectMetadata obj : listResult.getObjects()) {
        result.incrementScanned();
        long lastModified =
            obj.getLastModified() != null
                ? obj.getLastModified().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : 0;

        if (lastModified > 0 && lastModified < cutoffTime) {
          if (dryRun) {
            log.info(
                "[DryRun] 跳过删除过期文件: objectName={}, lastModified={}",
                obj.getObjectName(),
                obj.getLastModified());
            result.incrementSkipped();
          } else {
            try {
              storage.delete(bucketName, obj.getObjectName());
              result.incrementDeleted();
              log.debug("删除过期文件: objectName={}", obj.getObjectName());
            } catch (Exception e) {
              result.addError(obj.getObjectName(), e.getMessage());
              log.error("删除过期文件失败: objectName={}, error={}", obj.getObjectName(), e.getMessage());
            }
          }
        } else {
          result.incrementSkipped();
        }
      }

      cursor = listResult.getNextCursor();
      if (cursor == null || cursor.isEmpty()) {
        break;
      }
    }

    log.info(
        "生命周期规则执行完成: prefix={}, scanned={}, deleted={}, skipped={}",
        prefix,
        result.getScannedCount(),
        result.getDeletedCount(),
        result.getSkippedCount());
  }

  /**
   * 解析文件存储实例
   *
   * @return 文件存储实例
   */
  private IFileStorage resolveStorage() {
    return storageProvider.getStorage();
  }

  /** 清理结果统计 */
  @Data
  public static class CleanupResult {

    private int scannedCount = 0;
    private int deletedCount = 0;
    private int skippedCount = 0;
    private boolean success = true;
    private String message;
    private List<String> errors = new ArrayList<>(4);

    public CleanupResult() {}

    /**
     * 构造一个“被跳过”的清理结果。
     *
     * <p>用于未满足执行前置条件（如清理未启用、规则为空、无法获取存储实例）的场景， 此时 {@code success} 仍为 {@code true}，仅通过 {@code
     * message} 说明跳过原因，表示“非错误的中止”。
     *
     * @param message 跳过原因描述，便于调用方感知为何未执行实际清理
     * @return 标记为非错误的跳过结果
     */
    public static CleanupResult skipped(String message) {
      CleanupResult result = new CleanupResult();
      result.success = true;
      result.message = message;
      return result;
    }

    /**
     * 构造一个“失败”的清理结果。
     *
     * <p>用于无法继续执行清理的硬错误场景（与 {@link #skipped(String)} 的区别在于 {@code success} 置为 {@code
     * false}，下游可据此判定本次清理任务执行失败）。
     *
     * @param message 失败原因描述
     * @return 标记为失败的清理结果
     */
    public static CleanupResult failed(String message) {
      CleanupResult result = new CleanupResult();
      result.success = false;
      result.message = message;
      return result;
    }

    /**
     * 累加本轮已扫描的对象数。
     *
     * <p>无论对象最终被删除、跳过还是出错，只要被规则命中并进入处理流程即计数一次， 用于衡量扫描覆盖面；该方法只更新内存计数，不触碰任何存储对象。
     */
    public void incrementScanned() {
      scannedCount++;
    }

    /**
     * 累加实际删除成功的对象数。
     *
     * <p>仅在对象确实被物理删除后置为计数，dry-run 模式下不会调用此方法， 因此该值与真实删除量一致，可作为存储回收量的观测指标。
     */
    public void incrementDeleted() {
      deletedCount++;
    }

    /**
     * 累加被跳过删除的对象数。
     *
     * <p>用于命中清理规则但因 dry-run 预览、或不符合删除条件而未实际删除的对象计数， 与 {@link #incrementDeleted()} 互补，帮助区分“已删”与“待删”。
     */
    public void incrementSkipped() {
      skippedCount++;
    }

    /**
     * 记录单条对象处理失败的信息。
     *
     * <p>采用“失败归类不中断”策略：单条出错仅收集 {@code path: error} 到错误列表并继续后续对象， 保证部分失败不影响整体清理进度；{@code path} 与
     * {@code error} 均为非空，便于事后按路径回溯问题。
     *
     * @param path 出错对象的路径/键名，用于定位问题文件
     * @param error 具体错误信息
     */
    public void addError(String path, String error) {
      errors.add(path + ": " + error);
    }
  }
}
