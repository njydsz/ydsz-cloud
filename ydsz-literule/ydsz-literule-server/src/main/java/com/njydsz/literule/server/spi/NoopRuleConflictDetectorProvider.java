package com.njydsz.literule.server.spi;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 默认规则冲突检测提供者（空实现）。
 *
 * <p>当消费方（project 模块）未提供 {@link RuleConflictDetectorProvider} 的具体实现时，
 * 使用此空实现作为兜底，避免 Spring 容器因缺少 Bean 导致启动失败。
 *
 * <p>空实现返回空列表，表示无冲突检测能力（不报冲突），确保 literule-web 模块可正常启动。
 *
 * <p>若需启用实际冲突检测能力，消费方应提供自定义实现并标注 {@code @Primary} 或 {@code @ConditionalOnProperty}。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Component
public class NoopRuleConflictDetectorProvider implements RuleConflictDetectorProvider {

  /**
   * 空实现：不执行冲突检测，返回空列表。
   *
   * @return 永远返回空列表
   */
  @Override
  public List<RuleConflictInfo> detectConflicts() {
    return Collections.emptyList();
  }
}
