package com.njydsz.cronjob.server.core.executor;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.cronjob.domain.repository.JobNodeRepository;
import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.NodeConfig;
import com.njydsz.cronjob.server.core.metrics.SystemMetricsCollector;

/**
 * 节点心跳上报器（P0-14 节点上下线）。
 *
 * <p>应用启动后启动心跳线程，周期性向中心注册心跳（{@code ydsz_job_node} 表）， 标记本机为 ONLINE。Leader 节点的 {@link
 * JobNodeReaper} 根据此心跳检测离线节点并标记 OFFLINE。
 *
 * <h3>上下线行为</h3>
 *
 * <ul>
 *   <li><b>注册</b>：启动时通过存在即更新（Upsert by nodeId）方式注册节点， 如果节点不存在则插入新记录
 *   <li><b>心跳</b>：每 {@code ydsz.cronjob.node.heartbeat-interval-ms}（默认 10s）更新 lastHeartbeat
 *       + 系统指标（CPU / 内存使用率, runningCount）
 *       <ul>
 *         <li>节点记录不存在时的处理（db-sync 数据丢失场景）：重新注册节点而不是静默跳过
 *       </ul>
 *   <li><b>下线</b>：应用停止前（{@link PreDestroy}）更新 status=OFFLINE + 清理 metrics 指标
 * </ul>
 *
 * <hr>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(JobNodeRepository.class)
public class JobNodeHeartbeat {
  /** 心跳线程池终止等待（秒） */
  private static final long TERMINATION_WAIT_SECONDS = 3;


  private final JobNodeRepository jobNodeRepository;
  private final CronjobProperties cronjobProperties;
  private final SystemMetricsCollector metricsCollector;

  private ScheduledExecutorService heartbeatExecutor;
  private final AtomicBoolean running = new AtomicBoolean(false);

  /** 节点元信息缓存（注册时复用，避免每次心跳反射/环境查询） */
  private final AtomicReference<JobNodeVO> nodeInfo = new AtomicReference<>();

  /**
   * 应用就绪后启动心跳线程。
   *
   * <p>先到先得：先注册节点（ONLINE），再启动心跳循环。如果注册失败（如 DB 不可用）则不启动心跳。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    try {
      registerNode();
      startHeartbeatLoop();
      log.info("[NodeHeartbeat] 心跳线程启动成功: role={}", cronjobProperties.getLeader().getRole());
    } catch (Exception e) {
      log.error("[NodeHeartbeat] 启动失败: reason={}", e.getMessage(), e);
    }
  }

  /** 应用停止前标记 OFFLINE + 停止心跳线程。 */
  @PreDestroy
  public void shutdown() {
    try {
      markOffline();
      stopHeartbeatLoop();
      log.info("[NodeHeartbeat] 已标记 OFFLINE 并停止心跳");
    } catch (Exception e) {
      log.error("[NodeHeartbeat] shutdown 异常: reason={}", e.getMessage());
    }
  }

  /**
   * 注册或更新节点信息（upsert by nodeId）。
   *
   * <p>如果节点记录已存在则更新，否则插入新记录。心跳线程依赖节点记录存在，如果注册失败则后续心跳会触发 re-register 兜底。
   */
  public void registerNode() {
    try {
      JobNodeVO newNode = buildNodeInfo();
      nodeInfo.set(newNode);

      Optional<JobNodeVO> existing = jobNodeRepository.findById(newNode.getNodeId());
      if (existing.isPresent()) {
        // 更新已有记录
        newNode.setLastHeartbeat(LocalDateTime.now());
        newNode.setNodeStatus("ONLINE");
        int updated = jobNodeRepository.updateByNodeId(newNode);
        if (updated > 0) {
          log.info(
              "[NodeHeartbeat] 节点信息更新成功: nodeId={} nodeRole={}",
              newNode.getNodeId(),
              newNode.getNodeRole());
        } else {
          log.warn("[NodeHeartbeat] 节点更新影响行数为 0: nodeId={}", newNode.getNodeId());
          // 重新插入
          jobNodeRepository.insert(newNode);
        }
      } else {
        // 插入新记录
        newNode.setNodeStatus("ONLINE");
        newNode.setLastHeartbeat(LocalDateTime.now());
        newNode.setRunningCount(0);
        jobNodeRepository.insert(newNode);
        log.info(
            "[NodeHeartbeat] 节点注册成功: nodeId={} nodeRole={}",
            newNode.getNodeId(),
            newNode.getNodeRole());
      }
    } catch (Exception e) {
      log.error("[NodeHeartbeat] 节点注册失败: reason={}", e.getMessage(), e);
    }
  }

  /** 构建当前节点的 JobNodeVO。 */
  private JobNodeVO buildNodeInfo() {
    NodeConfig nodeConfig = cronjobProperties.getNode();
    JobNodeVO node = new JobNodeVO();
    node.setNodeId(resolveNodeId());
    node.setAppName(nodeConfig.getAppName());
    node.setNodeRole(cronjobProperties.getLeader().getRole());
    node.setHost(resolveHost());
    node.setPort(nodeConfig.getPort());
    node.setRunningCount(0);
    node.setNodeStatus("ONLINE");
    return node;
  }

  /**
   * 获取本节点 ID（供 WorkerNodeSelector 解析本地节点时使用）。
   *
   * @return 节点 ID（未注册时返回 null）
   */
  public String getNodeId() {
    JobNodeVO info = nodeInfo.get();
    return info != null ? info.getNodeId() : null;
  }

  /**
   * 任务开始通知（Dispatcher 心跳联动，当前心跳不跟踪运行计数，保留为扩展点）。
   *
   * <p>运行中任务数由 {@code SystemMetricsCollector.collectRunningCount()} 从 Redis 集群计数读取，
   * 无需在心跳组件内维护，故此处为空实现（保留钩子供未来按节点细分统计）。
   */
  public void onTaskStart() {
    // 预留：按节点维护 runningCount 时可在此增量更新节点记录
  }

  /**
   * 任务完成通知（Dispatcher 心跳联动，当前为空实现）。
   */
  public void onTaskComplete() {
    // 预留：与 onTaskStart 对称
  }

  /** 启动心跳循环（fixedRate, 每次以 daemon 线程执行）。 */
  private void startHeartbeatLoop() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    // 使用 common-thread ExecutorUtils 创建心跳线程池（符合云顶规范 15.4）
    heartbeatExecutor = ExecutorUtils.newScheduledThreadPool(1, "job-heartbeat-");
    long intervalMs = cronjobProperties.getNode().getHeartbeatIntervalMs();
    heartbeatExecutor.scheduleAtFixedRate(this::doHeartbeat, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
  }

  /** 停止心跳循环。 */
  private void stopHeartbeatLoop() {
    running.set(false);
    if (heartbeatExecutor != null) {
      heartbeatExecutor.shutdown();
      try {
        if (!heartbeatExecutor.awaitTermination(TERMINATION_WAIT_SECONDS, TimeUnit.SECONDS)) {
          heartbeatExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        heartbeatExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * 心跳主逻辑：更新 lastHeartbeat + metrics。
   *
   * <p>节点记录不存在时（db-sync 数据丢失）重新注册而不是静默跳过，确保 Leader 不会因短暂 DB
   * 不一致而标记本节点 OFFLINE。
   */
  private void doHeartbeat() {
    try {
      JobNodeVO info = nodeInfo.get();
      if (info == null) {
        registerNode();
        return;
      }
      BigDecimal cpuUsage = metricsCollector.collectCpuUsage();
      BigDecimal memUsage = metricsCollector.collectMemUsagePct();
      int runningCount = metricsCollector.collectRunningCount();
      LocalDateTime now = LocalDateTime.now();
      int updated =
          jobNodeRepository.updateHeartbeat(
              info.getNodeId(), now, runningCount, cpuUsage, memUsage, "ONLINE");
      if (updated == 0) {
        // 节点记录不存在（可能被意外删除），重新注册
        log.warn("[NodeHeartbeat] 节点记录丢失, 重新注册: nodeId={}", info.getNodeId());
        registerNode();
      }
    } catch (Exception e) {
      // 心跳失败不抛出，下次心跳再试，避免 cancel scheduled task
      log.warn("[NodeHeartbeat] 心跳执行异常(下次重试): reason={}", e.getMessage());
    }
  }

  /** 标记节点 OFFLINE（应用停止前）。 */
  private void markOffline() {
    try {
      JobNodeVO info = nodeInfo.get();
      if (info != null) {
        jobNodeRepository.updateStatus(info.getNodeId(), "OFFLINE", LocalDateTime.now());
      }
    } catch (Exception e) {
      log.warn("[NodeHeartbeat] 标记 OFFLINE 失败: reason={}", e.getMessage());
    }
  }

  /** 解析节点 ID（node-id 配置 > hostname:port > appId:port）。 */
  private String resolveNodeId() {
    NodeConfig nodeConfig = cronjobProperties.getNode();
    if (nodeConfig.getNodeId() != null && !nodeConfig.getNodeId().isBlank()) {
      return nodeConfig.getNodeId();
    }
    return nodeConfig.getAppName() + ":" + nodeConfig.getPort();
  }

  /** 尝试解析本机 hostname；解析失败时回退到 "unknown"。 */
  private String resolveHost() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }
}
