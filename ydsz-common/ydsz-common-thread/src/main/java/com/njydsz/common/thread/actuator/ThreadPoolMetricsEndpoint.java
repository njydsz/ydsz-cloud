package com.njydsz.common.thread.actuator;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.njydsz.common.thread.registry.ThreadPoolRegistry;

/**
 * Spring Boot Actuator 端点：{@code /actuator/threadpools}。
 *
 * <p>暴露 {@link ThreadPoolRegistry} 中所有已注册线程池的实时指标，
 * 便于运维人员通过 HTTP 接口快速查看线程池运行状态。
 *
 * <p>使用方式：
 *
 * <ul>
 *   <li>{@code GET /actuator/threadpools} - 返回所有线程池的指标快照列表
 *   <li>{@code GET /actuator/threadpools/{poolName}} - 返回指定线程池的指标快照
 *   <li>{@code GET /actuator/threadpools/{poolName}/dump} - 返回该线程池相关线程的栈帧 dump
 * </ul>
 *
 * <p>返回的 JSON 示例：
 *
 * <pre>{@code
 * {
 *   "threadPools": [
 *     {
 *       "poolName": "flowQueue",
 *       "corePoolSize": 2,
 *       "maximumPoolSize": 8,
 *       "activeCount": 0,
 *       "poolSize": 2,
 *       "queueSize": 0,
 *       "queueCapacity": 256,
 *       "completedTaskCount": 1523,
 *       "largestPoolSize": 3,
 *       "taskCount": 1523
 *     }
 *   ],
 *   "totalCount": 1
 * }
 * }</pre>
 *
 * <p>启用条件：classpath 上存在 Spring Boot Actuator（{@code spring-boot-actuator} 已声明为 optional 依赖）。
 *
 * <p>26.09.19 变更（P2-7）：新增 {@code /actuator/threadpools/{poolName}/dump} 端点输出线程栈帧。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
// CHECKSTYLE.OFF: RegexpSinglelineJava — 端点命名与 Spring Boot Actuator 规范一致
@Endpoint(id = "threadpools")
// CHECKSTYLE.ON: RegexpSinglelineJava
public class ThreadPoolMetricsEndpoint {

  /** 默认线程 dump 最大栈帧数。 */
  private static final int DEFAULT_MAX_STACK_DEPTH = 30;

  /**
   * 读取所有线程池的指标快照。
   *
   * @return 包含所有线程池指标快照的响应
   */
  @ReadOperation
  public ThreadPoolMetricsResponse listThreadPools() {
    List<ThreadPoolRegistry.ThreadPoolMetricsSnapshot> snapshots =
        ThreadPoolRegistry.snapshotMetrics();
    return new ThreadPoolMetricsResponse(snapshots, snapshots.size());
  }

  /**
   * 读取指定线程池的指标快照。
   *
   * @param poolName 线程池名称
   * @return 指定线程池的指标快照；不存在返回 {@code null}
   */
  @ReadOperation
  @Nullable
  public ThreadPoolRegistry.ThreadPoolMetricsSnapshot getThreadPool(@Selector String poolName) {
    return ThreadPoolRegistry.snapshotMetrics(poolName);
  }

  /**
   * 读取指定线程池的线程 dump 信息。
   *
   * <p>通过线程名前缀匹配（如 {@code ydsz-io-} 前缀）过滤出属于该线程池的线程，输出其栈帧快照。
   *
   * @param poolName 线程池配置 key
   * @return 线程 dump 响应；无法获取时返回错误描述
   */
  @ReadOperation
  public ThreadPoolDumpResponse getThreadPoolDump(@Selector String poolName) {
    List<ThreadDumpInfo> dumps = captureThreadDump(poolName);
    return new ThreadPoolDumpResponse(poolName, dumps, dumps.size());
  }

  /**
   * 捕获指定线程池的线程 dump。
   *
   * @param poolName 线程池名称
   * @return 线程 dump 列表
   */
  private List<ThreadDumpInfo> captureThreadDump(String poolName) {
    try {
      ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
      ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
      return Arrays.stream(threadInfos)
          .filter(info -> matchesPoolThread(info, poolName))
          .map(this::toThreadDumpInfo)
          .limit(256)
          .collect(Collectors.toList());
    } catch (Exception e) {
      ThreadDumpInfo errorInfo = new ThreadDumpInfo();
      errorInfo.error = e.getMessage();
      return List.of(errorInfo);
    }
  }

  /**
   * 判断线程是否属于目标线程池（基于线程名前缀匹配）。
   *
   * @param threadInfo 线程信息
   * @param poolName 线程池名称
   * @return true 如果线程属于目标线程池
   */
  private boolean matchesPoolThread(ThreadInfo threadInfo, String poolName) {
    String threadName = threadInfo.getThreadName();
    if (threadName == null || poolName == null) {
      return false;
    }
    // 匹配逻辑：线程名以 "ydsz-<poolName>-" 开头，或以 "<poolName>-" 开头
    return threadName.startsWith("ydsz-" + poolName + "-")
        || threadName.startsWith(poolName + "-");
  }

  /**
   * 将 ThreadInfo 转换为轻量 dump info（避免序列化过大的栈帧）。
   *
   * @param threadInfo JDK ThreadInfo
   * @return 线程 dump info
   */
  private ThreadDumpInfo toThreadDumpInfo(ThreadInfo threadInfo) {
    ThreadDumpInfo info = new ThreadDumpInfo();
    info.threadName = threadInfo.getThreadName();
    info.threadId = threadInfo.getThreadId();
    info.state = threadInfo.getThreadState().name();
    info.blockedTime = threadInfo.getBlockedTime();
    info.blockedCount = threadInfo.getBlockedCount();
    info.waitedTime = threadInfo.getWaitedTime();
    info.waitedCount = threadInfo.getWaitedCount();

    // 限制返回栈帧深度
    StackTraceElement[] stackTrace = threadInfo.getStackTrace();
    if (stackTrace != null && stackTrace.length > DEFAULT_MAX_STACK_DEPTH) {
      info.stackTrace =
          Arrays.stream(stackTrace)
              .limit(DEFAULT_MAX_STACK_DEPTH)
              .map(StackTraceElement::toString)
              .toArray(String[]::new);
      info.stackTraceTruncated = true;
      info.fullStackTraceDepth = stackTrace.length;
    } else if (stackTrace != null) {
      info.stackTrace =
          Arrays.stream(stackTrace).map(StackTraceElement::toString).toArray(String[]::new);
      info.stackTraceTruncated = false;
      info.fullStackTraceDepth = stackTrace.length;
    }

    // 死锁检测
    if (threadInfo.getThreadState() == Thread.State.BLOCKED && threadInfo.getLockName() != null) {
      info.lockName = threadInfo.getLockName();
      info.lockOwnerName = threadInfo.getLockOwnerName();
      info.lockOwnerId = threadInfo.getLockOwnerId();
    }
    return info;
  }

  // ==================== 内部数据类 ====================

  /**
   * 线程池指标端点响应体。
   *
   * @param threadPools 线程池指标快照列表
   * @param totalCount 线程池总数
   */
  public record ThreadPoolMetricsResponse(
      @NonNull List<ThreadPoolRegistry.ThreadPoolMetricsSnapshot> threadPools, int totalCount) {

    public List<ThreadPoolRegistry.ThreadPoolMetricsSnapshot> getThreadPools() {
      return threadPools;
    }

    public int getTotalCount() {
      return totalCount;
    }
  }

  /**
   * 线程 dump 端点响应体。
   *
   * @param poolName 线程池名称
   * @param threads 线程 dump 列表
   * @param threadCount 线程数量
   */
  public record ThreadPoolDumpResponse(@NonNull String poolName,
      @NonNull List<ThreadDumpInfo> threads, int threadCount) {

    public String getPoolName() {
      return poolName;
    }

    public List<ThreadDumpInfo> getThreads() {
      return threads;
    }

    public int getThreadCount() {
      return threadCount;
    }
  }

  /**
   * 单条线程 dump 信息（轻量级，避免传输过大 payload）。
   */
  public static final class ThreadDumpInfo {
    @Nullable public String error;
    @Nullable public String threadName;
    public long threadId;
    @Nullable public String state;
    public long blockedTime;
    public long blockedCount;
    public long waitedTime;
    public long waitedCount;
    @Nullable public String[] stackTrace;
    public boolean stackTraceTruncated;
    public int fullStackTraceDepth;
    @Nullable public String lockName;
    @Nullable public String lockOwnerName;
    public long lockOwnerId = -1;
  }
}
