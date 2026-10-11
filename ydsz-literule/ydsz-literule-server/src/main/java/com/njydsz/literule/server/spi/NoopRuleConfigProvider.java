package com.njydsz.literule.server.spi;

import java.util.Collections;
import java.util.List;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.server.spi.RuleConfigProvider;

/**
 * NO-OP 默认 RuleConfigProvider 实现（兜底方案）。
 *
 * <p>当无任何 RuleConfigProvider Bean 注册到容器时，本类作为兜底实现，保证容器启动不失败。
 *
 * <p>所有加载方法返回空列表，写操作抛出 UnsupportedOperationException
 * （消息提示"NoopRuleConfigProvider 不支持写入操作，请注册 RuleConfigProvider Bean"）。
 *
 * <p><b>使用场景：</b>开发/测试环境未配置规则引擎数据源时，避免因 Bean 链断裂导致服务启动失败。
 *
 * @since 26.10.08
 */
public class NoopRuleConfigProvider implements RuleConfigProvider {

  /** 不支持的写操作错误消息 */
  private static final String NOT_SUPPORTED_MSG =
      "NoopRuleConfigProvider 不支持写入操作，请注册 RuleConfigProvider Bean（如 DirectDbRuleConfigProvider 或实现 RuleConfigProvider SPI）";

  @Override
  public List<RuleDefinitionDTO> loadEnabledRules() {
    return Collections.emptyList();
  }

  @Override
  public List<RuleDefinitionDTO> loadAllRules() {
    return Collections.emptyList();
  }

  @Override
  public RuleDefinitionDTO save(RuleDefinitionDTO definition, String operator) {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }

  @Override
  public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }

  @Override
  public RuleDefinitionDTO findByCode(String ruleCode) {
    return null;
  }

  @Override
  public boolean isAvailable() {
    return false;
  }
}
