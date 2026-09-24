package com.njydsz.common.seata.health;

import com.njydsz.common.seata.aspect.SeataReflector;
import com.njydsz.common.seata.config.SeataProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/**
 * Seata 组件健康指示器。
 *
 * <p>通过 Actuator {@code /health} 暴露 Seata 状态：
 * <ul>
 *   <li>classpath 中存在 Seata 客户端且 {@code ydsz.seata.enabled=true}
 *       → {@code Status.UP}；</li>
 *   <li>classpath 中无 Seata 客户端（业务方未启用 Seata）
 *       → {@code Status.UP} + detail {@code seata=absent}（不告警）；</li>
 *   <li>Seata 客户端在 classpath 但开关未打开
 *       → {@code Status.UNKNOWN} + detail {@code seata=disabled}。</li>
 * </ul>
 *
 * <p>TC 联通性不在本健康检查范围内（需要真实 TC 地址与全局事务开启测试），
 * 仅用于判定"业务方是否引入 seata 客户端"与"common 行为开关状态"，
 * 避免在测试环境 / 单机部署中产生误报。
 *
 * @author ydsz-team
 * @since ACC-1
 */
@RequiredArgsConstructor
public class SeataHealthIndicator implements HealthIndicator {

  private final SeataProperties properties;

  @Override
  public Health health() {
    final boolean seataPresent = SeataReflector.seataPresent();
    if (seataPresent && properties.isEnabled()) {
      return Health.up().withDetail("seata", "enabled").build();
    }
    if (!seataPresent) {
      return Health.up().withDetail("seata", "absent").build();
    }
    return Health.status(Status.UNKNOWN).withDetail("seata", "present-but-disabled").build();
  }
}
