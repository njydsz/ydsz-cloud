package com.njydsz.literule.server.spi;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.dto.RuleDefinition;

/**
 * Apollo 配置中心规则数据源（P1-5）
 *
 * <p>从 Apollo 配置中心加载规则定义，支持配置变更监听。
 *
 * <p>使用方式：
 *
 * <pre>
 * ApolloRuleSource source = new ApolloRuleSource("rule-definitions");
 * source.init();
 * source.addChangeListener(rules -> log.info("规则已变更: {}", rules.size()));
 * </pre>
 *
 * <p>依赖：需在 classpath 中引入 {@code com.ctrip.framework.apollo:apollo-client}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class ApolloRuleSource implements RuleSource {

  private final String namespace;
  private final List<Consumer<List<RuleDefinition>>> listeners = new ArrayList<>();

  /** Apollo Config 实例（反射获取，避免硬依赖） */
  private Object apolloConfig;

  private volatile boolean initialized = false;

  public ApolloRuleSource(String namespace) {
    this.namespace = namespace;
  }

  @Override
  public SourceType getType() {
    return SourceType.APOLLO;
  }

  @Override
  public boolean supportsWatch() {
    return true;
  }

  @Override
  public boolean isAvailable() {
    return initialized && apolloConfig != null;
  }

  @Override
  public List<RuleDefinition> loadEnabledRules() {
    if (!isAvailable()) {
      return List.of();
    }
    try {
      // 反射调用 config.getProperty("rules", "[]")
      String json =
          (String)
              apolloConfig
                  .getClass()
                  .getMethod("getProperty", String.class, String.class)
                  .invoke(apolloConfig, "rules", "[]");
      return parseRulesFromJson(json);
    } catch (Exception e) {
      log.error("[ApolloRuleSource] 加载规则失败: {}", e.getMessage(), e);
      return List.of();
    }
  }

  @Override
  public void addChangeListener(Consumer<List<RuleDefinition>> listener) {
    listeners.add(listener);
  }

  @Override
  public void init() throws Exception {
    try {
      // 反射获取 Apollo ConfigService
      Class<?> configServiceClass = Class.forName("com.ctrip.framework.apollo.ConfigService");
      // ConfigService.getConfig(namespace)
      apolloConfig =
          configServiceClass.getMethod("getConfig", String.class).invoke(null, namespace);

      // 注册配置变更监听器
      Class<?> changeListenerClass =
          Class.forName("com.ctrip.framework.apollo.model.ConfigChangeListener");
      Object listener =
          Proxy.newProxyInstance(
              this.getClass().getClassLoader(),
              new Class[] {changeListenerClass},
              (proxy, method, args) -> {
                if ("onChange".equals(method.getName())) {
                  List<RuleDefinition> rules = loadEnabledRules();
                  for (Consumer<List<RuleDefinition>> l : listeners) {
                    try {
                      l.accept(rules);
                    } catch (Exception e) {
                      log.warn("[ApolloRuleSource] 监听器回调异常: {}", e.getMessage());
                    }
                  }
                }
                return null;
              });
      apolloConfig
          .getClass()
          .getMethod("addChangeListener", changeListenerClass)
          .invoke(apolloConfig, listener);

      initialized = true;
      log.info("[ApolloRuleSource] 已连接 Apollo: namespace={}", namespace);
    } catch (ClassNotFoundException e) {
      log.warn("[ApolloRuleSource] Apollo 客户端不在 classpath，数据源不可用");
      initialized = false;
    } catch (Exception e) {
      log.error("[ApolloRuleSource] 初始化失败: {}", e.getMessage(), e);
      initialized = false;
      throw e;
    }
  }

  private List<RuleDefinition> parseRulesFromJson(String json) {
    if (json == null || json.isBlank() || "[]".equals(json.trim())) {
      return List.of();
    }
    try {
      return YdszJson.parseArray(json, RuleDefinition.class);
    } catch (Exception e) {
      log.error("[ApolloRuleSource] JSON 解析失败: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * 根据编码查询单条规则定义
   *
   * <p>数据源为全量加载模型，此处通过已加载的启用规则列表过滤匹配，O(n) 复杂度。
   *
   * @param ruleCode 规则编码
   * @return 规则定义；不存在返回 null
   */
  @Override
  public RuleDefinition findByCode(String ruleCode) {
    if (ruleCode == null) {
      return null;
    }
    return loadEnabledRules().stream()
        .filter(r -> ruleCode.equals(r.getCode()))
        .findFirst()
        .orElse(null);
  }


  /**
   * 切换规则启停状态（配置中心数据源为只读，不支持写入操作）
   *
   * @param ruleCode 规则编码
   * @param enabled 是否启用
   * @param operator 操作人
   */
  @Override
  public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
    log.warn(
        "[{}] 配置中心数据源为只读，忽略 toggleEnabled 调用: ruleCode={}, enabled={}, operator={}",
        getClass().getSimpleName(), ruleCode, enabled, operator);
  }


  /**
   * 保存规则定义（配置中心数据源为只读，不支持写入操作）
   *
   * @param definition 规则定义
   * @param operator 操作人
   * @return 保存后的规则定义
   */
  @Override
  public RuleDefinition save(RuleDefinition definition, String operator) {
    throw new UnsupportedOperationException(
        "配置中心数据源为只读，不支持 save 操作: source=" + getClass().getSimpleName());
  }


  /**
   * 加载全部规则定义（含禁用）
   *
   * <p>配置中心数据源不区分启停状态，直接返回全量解析结果。
   *
   * @return 全部规则定义列表
   */
  @Override
  public List<RuleDefinition> loadAllRules() {
    return loadEnabledRules();
  }

}
