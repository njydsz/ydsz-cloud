package com.njydsz.common.util.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TempFileManager 生命周期测试。
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>创建-写入-跟踪：内容一致且计数正确
 *   <li>异常路径：写入失败时空文件被立即清理
 *   <li>显式删除 / 批量清理 / close() 优雅停机清理
 *   <li>TTL 兜底清理：超龄文件由后台任务回收
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class TempFileManagerTest {

  /** 普通测试用保留时长（足够长，确保不触发 TTL） */
  private static final Duration LONG_RETENTION = Duration.ofHours(1);

  /** 普通测试用清理间隔 */
  private static final Duration LONG_INTERVAL = Duration.ofMinutes(10);

  /** TTL 测试用保留时长（1ms，创建后立即超龄） */
  private static final Duration SHORT_RETENTION = Duration.ofMillis(1);

  /** TTL 测试用清理间隔 */
  private static final Duration SHORT_INTERVAL = Duration.ofMillis(50);

  /** TTL 清理轮询超时（毫秒） */
  private static final long TTL_POLL_TIMEOUT_MS = 5000L;

  /** TTL 清理轮询步长（毫秒） */
  private static final long TTL_POLL_STEP_MS = 50L;

  @TempDir Path tempDir;

  /** 每个用例创建的 manager（统一在 AfterEach 关闭，避免泄漏 sweeper 线程） */
  private final List<TempFileManager> managers = new ArrayList<>(4);

  @AfterEach
  void closeManagers() {
    managers.forEach(TempFileManager::close);
  }

  /**
   * 创建被测 manager 并登记以便统一关闭。
   *
   * @param retention 保留时长
   * @param interval 清理间隔
   * @return TempFileManager 实例
   */
  private TempFileManager newManager(Duration retention, Duration interval) {
    TempFileManager manager = new TempFileManager(retention, interval);
    managers.add(manager);
    return manager;
  }

  @Test
  @DisplayName("createAndWrite：内容写入正确并纳入跟踪")
  void createAndWriteTracksFile() throws IOException {
    TempFileManager manager = newManager(LONG_RETENTION, LONG_INTERVAL);
    byte[] content = "临时文件内容测试".getBytes(StandardCharsets.UTF_8);

    Path file = manager.createAndWrite("ydsz-test", ".tmp", new ByteArrayInputStream(content));

    assertThat(Files.exists(file)).isTrue();
    assertThat(Files.readAllBytes(file)).isEqualTo(content);
    assertThat(manager.getTrackedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("写入失败：异常路径上的空文件被立即删除")
  void writeFailureCleansUpEmptyFile() throws IOException {
    TempFileManager manager = newManager(LONG_RETENTION, LONG_INTERVAL);
    InputStream failingStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("simulated write failure");
          }
        };

    assertThatThrownBy(() -> manager.createAndWrite("ydsz-fail", ".tmp", failingStream))
        .isInstanceOf(IOException.class);

    assertThat(manager.getTrackedCount()).as("失败文件不应留在跟踪集合").isZero();
    assertThat(listFilesWithPrefix("ydsz-fail"))
        .as("失败路径上的空文件应被删除")
        .isEmpty();
  }

  @Test
  @DisplayName("deleteTracked：删除文件并从跟踪集合移除")
  void deleteTrackedRemovesFile() throws IOException {
    TempFileManager manager = newManager(LONG_RETENTION, LONG_INTERVAL);
    Path file =
        manager.createAndWrite(
            "ydsz-del", ".tmp", new ByteArrayInputStream(new byte[] {1, 2, 3}));

    manager.deleteTracked(file);

    assertThat(Files.exists(file)).isFalse();
    assertThat(manager.getTrackedCount()).isZero();
  }

  @Test
  @DisplayName("track：外部创建的文件可纳入统一管理")
  void trackExternalFile() throws IOException {
    TempFileManager manager = newManager(LONG_RETENTION, LONG_INTERVAL);
    Path external = Files.createTempFile(tempDir, "ydsz-external", ".tmp");

    manager.track(external);

    assertThat(manager.getTrackedCount()).isEqualTo(1);
    manager.deleteTracked(external);
    assertThat(Files.exists(external)).isFalse();
  }

  @Test
  @DisplayName("cleanupAll：批量删除全部跟踪文件")
  void cleanupAllRemovesEverything() throws IOException {
    TempFileManager manager = newManager(LONG_RETENTION, LONG_INTERVAL);
    manager.createAndWrite("ydsz-batch", ".tmp", new ByteArrayInputStream(new byte[] {1}));
    manager.createAndWrite("ydsz-batch", ".tmp", new ByteArrayInputStream(new byte[] {2}));

    manager.cleanupAll();

    assertThat(manager.getTrackedCount()).isZero();
    assertThat(listFilesWithPrefix("ydsz-batch")).isEmpty();
  }

  @Test
  @DisplayName("close()：停止调度器并清理全部跟踪文件（优雅停机语义）")
  void closeCleansUpAllFiles() throws IOException {
    TempFileManager manager = newManager(LONG_RETENTION, LONG_INTERVAL);
    Path file =
        manager.createAndWrite("ydsz-close", ".tmp", new ByteArrayInputStream(new byte[] {9}));
    managers.remove(manager);

    manager.close();

    assertThat(Files.exists(file)).isFalse();
    assertThat(manager.getTrackedCount()).isZero();
  }

  @Test
  @DisplayName("TTL 兜底清理：超龄文件由后台任务自动回收")
  void ttlSweeperRemovesExpiredFiles() throws Exception {
    TempFileManager manager = newManager(SHORT_RETENTION, SHORT_INTERVAL);
    Path file =
        manager.createAndWrite(
            "ydsz-ttl", ".tmp", new ByteArrayInputStream(new byte[] {4, 5, 6}));

    assertThat(waitUntilDeleted(file, TTL_POLL_TIMEOUT_MS, TTL_POLL_STEP_MS))
        .as("超龄文件应在 TTL 清理周期内被回收").isTrue();
    assertThat(manager.getTrackedCount()).isZero();
  }

  /**
   * 轮询等待文件被删除。
   *
   * @param file 目标文件
   * @param timeoutMs 超时（毫秒）
   * @param stepMs 轮询步长（毫秒）
   * @return 文件在超时前被删除返回 true
   * @throws InterruptedException 等待被中断时
   */
  private static boolean waitUntilDeleted(Path file, long timeoutMs, long stepMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (Files.notExists(file)) {
        return true;
      }
      Thread.sleep(stepMs);
    }
    return Files.notExists(file);
  }

  /**
   * 列出系统临时目录中指定前缀的残留文件（用于断言清理彻底性）。
   *
   * @param prefix 文件名前缀
   * @return 残留文件路径列表
   * @throws IOException 列目录失败时
   */
  private static List<Path> listFilesWithPrefix(String prefix) throws IOException {
    List<Path> matches = new ArrayList<>(2);
    try (var stream = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      stream.filter(path -> path.getFileName().toString().startsWith(prefix))
          .forEach(matches::add);
    }
    return matches;
  }
}
