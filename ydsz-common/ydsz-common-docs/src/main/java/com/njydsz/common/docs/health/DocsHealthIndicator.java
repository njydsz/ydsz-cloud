package com.njydsz.common.docs.health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.common.docs.security.pii.PiiDetector;
import com.njydsz.common.docs.service.AsyncDocumentParser;

/**
 * 文档处理模块健康指标
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class DocsHealthIndicator implements HealthIndicator {

  private final DocumentParserRegistry parserRegistry;
  private final List<PiiDetector> piiDetectors;
  private final DocsProperties properties;
  private final AsyncDocumentParser asyncDocumentParser;

  /**
   * 汇报文档处理模块的运行时健康状态。
   *
   * <p>本指标为<b>只读探针</b>，不触发任何解析动作，因此可被 Actuator 高频轮询。 采集四类信息：模块开关与体积上限（来自 {@link DocsProperties}）、
   * 已装配的解析器所支持的格式、已装配的 PII 检测器类型清单， 以及异步解析线程池的队列积压量与活跃线程数。
   *
   * <p><b>始终返回 UP：</b>本方法不会返回 DOWN。因为解析器/检测器是按 {@code @ConditionalOnClass}
   * 可选装配的，缺失某个格式属于预期配置结果而非故障， 若据此判定 DOWN 会导致实例被误摘流量。异步线程池指标读取失败时， 也仅以 {@code asyncError}
   * 明细项记录异常信息并继续返回 UP， 由运维依据明细自行判断，而不是让整个应用健康检查失败。
   *
   * @return 状态恒为 UP 的健康报告，明细含 {@code enabled}、{@code maxFileSizeMb}、 {@code
   *     supportedFormats}、{@code piiDetectors}、{@code asyncQueueSize}、 {@code
   *     asyncActiveCount}；线程池读取异常时以 {@code asyncError} 替代后两项
   */
  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(16);
    details.put("enabled", properties.isEnabled());
    details.put("maxFileSizeMb", properties.getMaxFileSizeMb());
    details.put("supportedFormats", parserRegistry.getSupportedFormats());
    details.put(
        "piiDetectors", piiDetectors.stream().map(d -> d.getSupportedType().name()).toList());
    try {
      details.put("asyncQueueSize", asyncDocumentParser.getQueueSize());
      details.put("asyncActiveCount", asyncDocumentParser.getActiveCount());
    } catch (Exception e) {
      details.put("asyncError", e.getMessage());
    }
    return Health.up().withDetails(details).build();
  }
}
