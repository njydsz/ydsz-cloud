package com.njydsz.cronjob.server.core.executor;

import java.math.BigDecimal;
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
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(JobNodeRepository.class)
public class JobNodeHeartbeat {

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
        newNode.setStatus("ONLINE");
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
        newNode.setStatus("ONLINE");
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
    CronjobProperties.Leader leader = cronjobProperties.getLeader();
    JobNodeVO node = new JobNodeVO();
    node.setNodeId(resolveNodeId());
    node.setAppName(leader.getAppname());
    node.setNodeRole(leader.getRole());
    node.setHost(resolveHost());
    node.setPort(leader.getPort());
    node.setRunningCount(0);
    node.setStatus("ONLINE");
    return node;
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
        if (!heartbeatExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
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
    CronjobProperties.Leader leader = cronjobProperties.getLeader();
    if (leader.getNodeId() != null && !leader.getNodeId().isBlank()) {
      return leader.getNodeId();
    }
    return leader.getAppname() + ":" + leader.getPort();
  }

  /** 尝试解析本机 hostname；解析失败时回退到 "unknown"。 */
  private String resolveHost() {
    try {
      return java.net.InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }
}
