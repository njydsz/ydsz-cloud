package com.njydsz.cronjob.server.core.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.core.dispatch.DependencyPatrolScanner;

/**
 * 依赖巡检扫描任务（P2-O3：统一扫描器）。
 *
 * <p>委托 {@link DependencyPatrolScanner} 巡检 DAG 定义中的节点引用完整性。
 * 扫描间隔由配置 {@code ydsz.cronjob.dependency-patrol.interval-ms} 控制（默认 10min）。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DependencyPatrolScanTask implements ScanTask {

  private final DependencyPatrolScanner dependencyPatrolScanner;

  @Override
  public String name() {
    return "dependency-patrol";
  }

  @Override
  public void scan() {
    dependencyPatrolScanner.patrol();
  }

  @Override
  public long intervalMs() {
    // 使用 DependencyPatrolScanner 的固定间隔（10min）
    return 600000L;
  }

  @Override
  public String lockKey() {
    return "cronjob:dependency-patrol";
  }
}
