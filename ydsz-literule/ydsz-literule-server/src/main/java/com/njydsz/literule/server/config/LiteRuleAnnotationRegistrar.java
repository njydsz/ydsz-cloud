package com.njydsz.literule.server.config;

import java.util.Map;
import java.util.StringTokenizer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.annotation.LiteRule;
import com.njydsz.literule.server.impl.ExpressionRule;

/**
 * 声明式规则注册器（P2-10）
 *
 * <p>容器刷新完成后扫描 {@code @LiteRule}（标注在 {@link Rule} Spring Bean 上）， 将其注册到 {@link RuleEngine}；同时按
 * {@code ydsz.literule.annotation-scan-base-packages} 配置的基包扫描 {@link RuleDefinitionDTO}
 * Bean（纯声明式表达式规则）并按需包装为 {@link ExpressionRule} 后注册。
 *
 * <p>实现为 {@link SmartInitializingSingleton}，在所有非懒加载单例实例化之后、 容器完全启动之前执行一次，确保扫描到的 Bean 全部就绪。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @LiteRule
 * @Component
 * public class OverdueRule implements Rule { ... }
 *
 * @Component
 * public class MyDef extends RuleDefinitionDTO { ... }   // 通过 @RuleDefinitionMeta 标注或被 @Component 声明
 * }</pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class LiteRuleAnnotationRegistrar implements SmartInitializingSingleton {

  /** 引擎 */
  private final RuleEngine ruleEngine;

  /** 表达式求值器 */
  private final ExpressionEngine evaluator;

  /** Spring 容器 */
  private final ApplicationContext applicationContext;

  /** 配置属性（提供扫描基包） */
  private final LiteRuleProperties properties;

  public LiteRuleAnnotationRegistrar(
      RuleEngine ruleEngine,
      ExpressionEngine evaluator,
      ApplicationContext applicationContext,
      LiteRuleProperties properties) {
    this.ruleEngine = ruleEngine;
    this.evaluator = evaluator;
    this.applicationContext = applicationContext;
    this.properties = properties;
  }

  @Override
  public void afterSingletonsInstantiated() {
    int liteRuleCount = scanLiteRuleBeans();
    int defMetaCount = scanRuleDefinitionBeans();
    log.info(
        "[LiteRule-Annotation] 声明式规则注册完成: @LiteRule={}, @RuleDefinitionDTO={}",
        liteRuleCount,
        defMetaCount);
  }

  /** 扫描容器中所有带 {@link LiteRule} 注解的 {@link Rule} Bean。 */
  private int scanLiteRuleBeans() {
    Map<String, Object> beans = applicationContext.getBeansWithAnnotation(LiteRule.class);
    int count = 0;
    for (Object bean : beans.values()) {
      if (!(bean instanceof Rule)) {
        log.warn("[LiteRule-Annotation] 跳过非 Rule 类型: bean={}", bean.getClass().getName());
        continue;
      }
      LiteRule anno = AnnotationUtils.findAnnotation(bean.getClass(), LiteRule.class);
      if (anno != null && !anno.enabled()) {
        log.info("[LiteRule-Annotation] 规则已禁用: rule={}", ((Rule) bean).getCode());
        continue;
      }
      try {
        ruleEngine.register((Rule) bean);
        count++;
      } catch (Exception e) {
        log.error(
            "[LiteRule-Annotation] 注册规则失败: rule={} err={}",
            ((Rule) bean).getCode(),
            e.getMessage(),
            e);
      }
    }
    return count;
  }

  /**
   * 按配置的基包扫描 {@link RuleDefinitionDTO} Bean，包装为 {@link ExpressionRule} 后注册。
   *
   * <p>未配置 {@code annotation-scan-base-packages} 时跳过本步骤。
   */
  private int scanRuleDefinitionBeans() {
    String basePackages = properties.getAnnotationScanBasePackages();
    if (basePackages == null || basePackages.isBlank()) {
      return 0;
    }
    int count = 0;
    StringTokenizer st = new StringTokenizer(basePackages, ",");
    while (st.hasMoreTokens()) {
      String pkg = st.nextToken().trim();
      if (pkg.isEmpty()) {
        continue;
      }
      // 仅扫描标注了 @Component 的 RuleDefinitionDTO 子类（避免误将抽象/接口类实例化）
      Map<String, RuleDefinitionDTO> defBeans =
          applicationContext.getBeansOfType(RuleDefinition.class);
      for (RuleDefinitionDTO bean : defBeans.values()) {
        if (!bean.getClass().getPackageName().startsWith(pkg)) {
          continue;
        }
        if (applicationContext.findAnnotationOnBean(bean.toString(), Component.class) == null) {
          continue;
        }
        try {
          ruleEngine.register(new ExpressionRule((RuleDefinitionDTO) bean, evaluator));
          count++;
        } catch (Exception e) {
          log.error(
              "[LiteRule-Annotation] 注册 RuleDefinitionDTO 失败: def={} err={}",
              ((RuleDefinitionDTO) bean).getCode(),
              e.getMessage(),
              e);
        }
      }
    }
    return count;
  }
}
