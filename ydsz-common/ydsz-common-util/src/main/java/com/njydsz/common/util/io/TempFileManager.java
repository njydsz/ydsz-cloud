package com.njydsz.common.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 临时文件统一管理器
 *
 * <p>集中管理应用运行过程中产生的临时文件，提供：
 *
 * <ul>
 *   <li>带前缀的统一创建方法
 *   <li>跟踪所有已创建的临时文件，支持批量清理
 *   <li>TTL 兜底清理：创建后超过保留时长（{@code ydsz.util.tempfile.retention}，默认 24h）仍未删除的文件由后台任务定期回收
 *   <li>注册 JVM ShutdownHook 兜底清理，防止文件泄漏
 * </ul>
 *
 * <p><b>设计意图：</b>文档解析、安全扫描、异步处理、OCR 识别、文件上传等多处 需要创建临时文件，原实现散落各处且各自清理。引入此组件后可在应用层面获得
 * 临时文件的全局视图与兜底保障，避免因异常路径遗漏清理导致磁盘空间泄漏。
 *
 * <p><b>适用范围：</b>全系统通用能力，不限于文档处理场景。任何需要创建临时文件 并确保最终清理的业务模块均可注入使用。
 *
 * <p><b>注册方式：</b>由 {@code UtilAutoConfiguration} 以 {@code @Bean} 注册（不使用 {@code @Component}，
 * 遵循模块"不依赖业务侧组件扫描"的装配原则）。业务方也可自定义 Bean 覆盖（{@code @ConditionalOnMissingBean}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TempFileManager implements AutoCloseable {

  /** 本组件创建的所有临时文件路径 → 创建时间（毫秒） */
  private final Map<Path, Long> trackedFiles = new ConcurrentHashMap<>();

  /** 兜底清理调度器（单线程守护线程，不阻碍 JVM 退出） */
  private final ScheduledExecutorService sweeper;

  /** 临时文件保留时长（毫秒），超龄文件由兜底任务清理 */
  private final long retentionMillis;

  /** 默认临时文件保留时长：24 小时 */
  private static final Duration DEFAULT_RETENTION = Duration.ofHours(24);

  /** 默认兜底清理任务执行间隔：10 分钟 */
  private static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(10);

  /**
   * 以默认配置构造（保留 24h，每 10 分钟清理一次）。
   */
  public TempFileManager() {
    this(DEFAULT_RETENTION, DEFAULT_CLEANUP_INTERVAL);
  }

  /**
   * 以指定配置构造。
   *
   * @param retention 临时文件保留时长（正数）
   * @param cleanupInterval 兜底清理任务执行间隔（正数）
   */
  public TempFileManager(Duration retention, Duration cleanupInterval) {
    this.retentionMillis = retention.toMillis();
    // CHECKSTYLE.OFF: RegexpSinglelineJava — L1 工具模块禁止向下依赖 ydsz-common-thread，此处为兜底清理调度器（单线程守护线程），属短生命周期内部线程池
    this.sweeper =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "ydsz-tempfile-sweeper");
              thread.setDaemon(true);
              return thread;
            });
    // CHECKSTYLE.ON: RegexpSinglelineJava
    sweeper.scheduleWithFixedDelay(
        this::sweepExpired,
        cleanupInterval.toMillis(),
        cleanupInterval.toMillis(),
        TimeUnit.MILLISECONDS);
    // JVM 退出时的兜底清理，处理未显式关闭的临时文件
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("[TempFileManager] 应用退出，清理残留临时文件 {} 个", trackedFiles.size());
                  cleanupAll();
                },
                "ydsz-tempfile-cleanup"));
  }

  /**
   * 创建受跟踪的临时文件并将输入流写入其中。
   *
   * <p>创建成功后路径会自动注册到跟踪集合，后续可通过 {@link #track(Path)}、{@link #deleteTracked(Path)} 或 {@link
   * #cleanupAll()} 管理。 若写入过程中发生异常，已创建的空文件会被立即删除。
   *
   * @param prefix 文件名前缀（不含路径），不可为 {@code null}
   * @param suffix 文件名后缀（含点），可为 {@code null} 表示 {@code .tmp}
   * @param inputStream 数据源流，写入后<b>不</b>由此方法关闭
   * @return 已写入完成的临时文件路径
   * @throws IOException 创建文件或写入失败时抛出，异常路径上的空文件会被清理
   */
  public Path createAndWrite(String prefix, String suffix, InputStream inputStream)
      throws IOException {
    Path tempFile = Files.createTempFile(prefix, suffix);
    try {
      inputStream.transferTo(Files.newOutputStream(tempFile));
      trackedFiles.put(tempFile, System.currentTimeMillis());
      return tempFile;
    } catch (IOException e) {
      // 写入失败时删除空文件，避免残留
      deleteQuietly(tempFile);
      throw e;
    }
  }

  /**
   * 将外部创建的临时文件纳入跟踪管理。
   *
   * <p>对于不方便直接使用 {@link #createAndWrite} 的场景（如需使用 {@code Files.createTempDirectory()}
   * 等变体），可手动注册以便统一清理。
   *
   * @param tempFile 已存在的临时文件路径，为 {@code null} 时忽略
   */
  public void track(Path tempFile) {
    if (tempFile != null) {
      trackedFiles.put(tempFile, System.currentTimeMillis());
    }
  }

  /**
   * 删除指定临时文件并从跟踪集合移除。
   *
   * <p>删除失败仅记录 debug 日志，不抛出异常。这是有意的设计—— 临时文件清理不应影响主业务流程的后续步骤。
   *
   * @param tempFile 要删除的临时文件路径，为 {@code null} 时忽略
   */
  public void deleteTracked(Path tempFile) {
    if (tempFile != null) {
      trackedFiles.remove(tempFile);
      deleteQuietly(tempFile);
    }
  }

  /**
   * 返回当前未被清理的临时文件数量。
   *
   * <p>该指标可暴露到监控系统，持续增长说明消费能力不足或存在泄漏。
   *
   * @return 仍在跟踪集合中的临时文件数量
   */
  public int getTrackedCount() {
    return trackedFiles.size();
  }

  /**
   * 强制清理所有被跟踪的临时文件。
   *
   * <p>通常在应用关闭或批次处理结束时调用，无论单个文件是否删除成功， 都会将路径从跟踪集合中移除（避免无限重试）。无论何种情况此方法不会抛异常。
   */
  public void cleanupAll() {
    trackedFiles.keySet().forEach(this::deleteQuietly);
    trackedFiles.clear();
  }

  /**
   * 停止兜底清理任务并清理全部跟踪文件（容器优雅停机时由 Spring 调用）。
   */
  @Override
  public void close() {
    sweeper.shutdownNow();
    cleanupAll();
  }

  /** 清理超过保留时长的临时文件（由调度器周期调用，异常不外抛以保证任务持续运行）。 */
  private void sweepExpired() {
    try {
      long now = System.currentTimeMillis();
      int swept = 0;
      for (Map.Entry<Path, Long> entry : trackedFiles.entrySet()) {
        if (now - entry.getValue() >= retentionMillis) {
          if (trackedFiles.remove(entry.getKey(), entry.getValue())) {
            deleteQuietly(entry.getKey());
            swept++;
          }
        }
      }
      if (swept > 0) {
        log.info("[TempFileManager] TTL 兜底清理超龄临时文件 {} 个（保留时长 {}ms）", swept, retentionMillis);
      }
    } catch (Exception e) {
      // 调度任务不允许因单次异常终止（scheduleWithFixedDelay 的语义）
      log.warn("[TempFileManager] TTL 清理任务执行异常: {}", e.getMessage());
    }
  }

  /**
   * 静默删除单个文件，失败只记录而不传播异常。
   *
   * @param path 待删除的文件路径，为 {@code null} 时忽略
   */
  private void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.debug("[TempFileManager] 临时文件删除失败: {}", path, e);
    }
  }
}
