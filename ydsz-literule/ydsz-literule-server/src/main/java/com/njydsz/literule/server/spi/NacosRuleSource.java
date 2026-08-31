package com.njydsz.literule.server.spi;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.api.RuleDefinition;

/**
 * Nacos 配置中心规则数据源（P1-5）
 *
 * <p>从 Nacos 配置中心加载规则定义，支持 ConfigService 监听规则变更。
 *
 * <p>使用方式：
 *
 * <pre>
 * NacosRuleSource source = new NacosRuleSource("127.0.0.1:8848", "rule-definitions");
 * source.init();
 * source.addChangeListener(rules -> log.info("规则已变更: {}", rules.size()));
 * </pre>
 *
 * <p>依赖：需在 classpath 中引入 {@code com.alibaba.nacos:nacos-client}。 当 Nacos 客户端不在 classpath 中时，{@link
 * #isAvailable()} 返回 false，不参与数据源选择。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class NacosRuleSource implements RuleSource {

    /** 配置拉取超时（毫秒） */
  private static final long CONFIG_TIMEOUT_MS = 5000L;

  private final String serverAddr;
  private final String dataId;
  private final String group;
  private final List<Consumer<List<RuleDefinition>>> listeners = new ArrayList<>();

  /** Nacos ConfigService 实例（反射创建，避免硬依赖） */
  private Object configService;

  /** 是否已初始化 */
  private volatile boolean initialized = false;

  /**
   * 构造 Nacos 规则数据源
   *
   * @param serverAddr Nacos 服务地址（如 "127.0.0.1:8848"）
   * @param dataId 配置 Data ID（如 "rule-definitions"）
   */
  public NacosRuleSource(String serverAddr, String dataId) {
    this(serverAddr, dataId, "DEFAULT_GROUP");
  }

  /**
   * 构造 Nacos 规则数据源
   *
   * @param serverAddr Nacos 服务地址
   * @param dataId 配置 Data ID
   * @param group 配置 Group
   */
  public NacosRuleSource(String serverAddr, String dataId, String group) {
    this.serverAddr = serverAddr;
    this.dataId = dataId;
    this.group = group;
  }

  @Override
  public SourceType getType() {
    return SourceType.NACOS;
  }

  @Override
  public boolean supportsWatch() {
    return true;
  }

  @Override
  public boolean isAvailable() {
    return initialized && configService != null;
  }

  @Override
  public List<RuleDefinition> loadEnabledRules() {
    if (!isAvailable()) {
      log.warn("[NacosRuleSource] 未初始化或不可用，返回空列表");
      return List.of();
    }
    try {
      // 反射调用 configService.getConfig(dataId, group, 5000)
      Object config =
          configService
              .getClass()
              .getMethod("getConfig", String.class, String.class, long.class)
              .invoke(configService, dataId, group, CONFIG_TIMEOUT_MS);
      if (config == null) {
        return List.of();
      }
      return parseRulesFromJson(String.valueOf(config));
    } catch (Exception e) {
      log.error("[NacosRuleSource] 加载规则失败: {}", e.getMessage(), e);
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
      // 反射创建 Nacos ConfigService，避免硬依赖 nacos-client
      Class<?> factoryClass = Class.forName("com.alibaba.nacos.api.NacosFactory");
      // 触发 ConfigService 类加载（NacosFactory.createConfigService 内部会引用）
      Class.forName("com.alibaba.nacos.api.config.ConfigService");
      // properties.put("serverAddr", serverAddr)
      Properties properties = new Properties();
      properties.put("serverAddr", serverAddr);
      // NacosFactory.createConfigService(properties)
      configService =
          factoryClass.getMethod("createConfigService", Properties.class).invoke(null, properties);

      // 注册配置变更监听器：configService.addListener(dataId, group, listener)
      Object listener = createConfigListener();
      configService
          .getClass()
          .getMethod(
              "addListener",
              String.class,
              String.class,
              Class.forName("com.alibaba.nacos.api.config.listener.Listener"))
          .invoke(configService, dataId, group, listener);

      initialized = true;
      log.info(
          "[NacosRuleSource] 已连接 Nacos: serverAddr={}, dataId={}, group={}",
          serverAddr,
          dataId,
          group);
    } catch (ClassNotFoundException e) {
      log.warn("[NacosRuleSource] Nacos 客户端不在 classpath，数据源不可用: {}", e.getMessage());
      initialized = false;
    } catch (Exception e) {
      log.error("[NacosRuleSource] 初始化失败: {}", e.getMessage(), e);
      initialized = false;
      throw e;
    }
  }

  @Override
  public void destroy() throws Exception {
    if (configService != null) {
      try {
        configService.getClass().getMethod("shutDown").invoke(configService);
        log.info("[NacosRuleSource] 连接已关闭");
      } catch (Exception e) {
        log.debug("[NacosRuleSource] 关闭异常: {}", e.getMessage());
      }
    }
  }

  /** 创建 Nacos 配置监听器（反射，避免硬依赖） */
  private Object createConfigListener() throws Exception {
    Class<?> listenerClass = Class.forName("com.alibaba.nacos.api.config.listener.Listener");
    return Proxy.newProxyInstance(
        this.getClass().getClassLoader(),
        new Class[] {listenerClass},
        (proxy, method, args) -> {
          if ("receiveConfigInfo".equals(method.getName())) {
            String newConfig = String.valueOf(args[0]);
            List<RuleDefinition> rules = parseRulesFromJson(newConfig);
            for (Consumer<List<RuleDefinition>> listener : listeners) {
              try {
                listener.accept(rules);
              } catch (Exception e) {
                log.warn("[NacosRuleSource] 监听器回调异常: {}", e.getMessage());
              }
            }
          }
          return null;
        });
  }

  /**
   * 从 JSON 解析规则定义列表
   *
   * <p>使用 fastjson2 解析，格式为 {@code List<RuleDefinition>} 的 JSON 序列化。
   */
  private List<RuleDefinition> parseRulesFromJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return YdszJson.parseArray(json, RuleDefinition.class);
    } catch (Exception e) {
      log.error("[NacosRuleSource] JSON 解析失败: {}", e.getMessage());
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
