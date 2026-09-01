package com.njydsz.literule.server.spi;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.dto.RuleDefinition;

/**
 * ZooKeeper 规则数据源（P1-5）
 *
 * <p>从 ZooKeeper 节点加载规则定义，支持 NodeCache 监听节点变更。
 *
 * <p>使用方式：
 *
 * <pre>
 * ZookeeperRuleSource source = new ZookeeperRuleSource("127.0.0.1:2181", "/literule/definitions");
 * source.init();
 * source.addChangeListener(rules -> log.info("规则已变更: {}", rules.size()));
 * </pre>
 *
 * <p>依赖：需在 classpath 中引入 {@code org.apache.curator:curator-recipes}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class ZookeeperRuleSource implements RuleSource {

    /** 重试策略：基础休眠时间（毫秒） */
  private static final int RETRY_SLEEP_MS = 1000;

  /** 重试策略：最大重试次数 */
  private static final int RETRY_MAX_TIMES = 3;

  private final String connectString;
  private final String path;
  private final List<Consumer<List<RuleDefinition>>> listeners = new ArrayList<>();

  /** CuratorFramework 实例（反射创建，避免硬依赖） */
  private Object client;

  private volatile boolean initialized = false;

  public ZookeeperRuleSource(String connectString, String path) {
    this.connectString = connectString;
    this.path = path;
  }

  @Override
  public SourceType getType() {
    return SourceType.ZOOKEEPER;
  }

  @Override
  public boolean supportsWatch() {
    return true;
  }

  @Override
  public boolean isAvailable() {
    return initialized && client != null;
  }

  @Override
  public List<RuleDefinition> loadEnabledRules() {
    if (!isAvailable()) {
      return List.of();
    }
    try {
      // 反射调用 client.getData().forPath(path)
      Object dataBuilder = client.getClass().getMethod("getData").invoke(client);
      byte[] data =
          (byte[])
              dataBuilder.getClass().getMethod("forPath", String.class).invoke(dataBuilder, path);
      if (data == null || data.length == 0) {
        return List.of();
      }
      String json = new String(data, StandardCharsets.UTF_8);
      return parseRulesFromJson(json);
    } catch (Exception e) {
      log.error("[ZookeeperRuleSource] 加载规则失败: {}", e.getMessage(), e);
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
      // 反射创建 CuratorFramework
      Class<?> builderClass = Class.forName("org.apache.curator.framework.CuratorFrameworkFactory");
      // CuratorFrameworkFactory.builder()
      Object builder = builderClass.getMethod("builder").invoke(null);
      // .connectString(connectString)
      builder =
          builder
              .getClass()
              .getMethod("connectString", String.class)
              .invoke(builder, connectString);
      // .retryPolicy(new ExponentialBackoffRetry(1000, 3))
      Class<?> retryPolicyClass = Class.forName("org.apache.curator.retry.ExponentialBackoffRetry");
      Object retryPolicy =
          retryPolicyClass.getConstructor(int.class, int.class).newInstance(RETRY_SLEEP_MS, RETRY_MAX_TIMES);
      builder =
          builder
              .getClass()
              .getMethod("retryPolicy", Class.forName("org.apache.curator.RetryPolicy"))
              .invoke(builder, retryPolicy);
      // .build()
      client = builder.getClass().getMethod("build").invoke(builder);
      // client.start()
      client.getClass().getMethod("start").invoke(client);

      // 确保路径存在
      try {
        Object createBuilder = client.getClass().getMethod("create").invoke(client);
        createBuilder.getClass().getMethod("creatingParentsIfNeeded").invoke(createBuilder);
        createBuilder.getClass().getMethod("forPath", String.class).invoke(createBuilder, path);
      } catch (Exception e) {
        // 节点已存在
        log.debug("[ZookeeperRuleSource] ZNode 已存在，跳过创建: path={}, err={}", path, e.getMessage());
      }

      // 注册 NodeCache 监听器
      registerNodeCache();

      initialized = true;
      log.info(
          "[ZookeeperRuleSource] 已连接 ZooKeeper: connectString={}, path={}", connectString, path);
    } catch (ClassNotFoundException e) {
      log.warn("[ZookeeperRuleSource] Curator 不在 classpath，数据源不可用");
      initialized = false;
    } catch (Exception e) {
      log.error("[ZookeeperRuleSource] 初始化失败: {}", e.getMessage(), e);
      initialized = false;
      throw e;
    }
  }

  /** 注册 NodeCache 监听节点变更 */
  private void registerNodeCache() throws Exception {
    try {
      Class<?> nodeCacheClass =
          Class.forName("org.apache.curator.framework.recipes.cache.NodeCache");
      Object nodeCache =
          nodeCacheClass
              .getConstructor(
                  Class.forName("org.apache.curator.framework.CuratorFramework"), String.class)
              .newInstance(client, path);
      // nodeCache.getListenable().addListener(listener)
      Object listenable = nodeCacheClass.getMethod("getListenable").invoke(nodeCache);
      Class<?> listenerClass =
          Class.forName("org.apache.curator.framework.recipes.cache.NodeCacheListener");

      Object listener =
          Proxy.newProxyInstance(
              this.getClass().getClassLoader(),
              new Class[] {listenerClass},
              (proxy, method, args) -> {
                if ("nodeChanged".equals(method.getName())) {
                  List<RuleDefinition> rules = loadEnabledRules();
                  for (Consumer<List<RuleDefinition>> l : listeners) {
                    try {
                      l.accept(rules);
                    } catch (Exception e) {
                      log.warn("[ZookeeperRuleSource] 监听器回调异常: {}", e.getMessage());
                    }
                  }
                }
                return null;
              });
      listenable.getClass().getMethod("addListener", listenerClass).invoke(listenable, listener);
      // nodeCache.start(true)
      nodeCacheClass.getMethod("start", boolean.class).invoke(nodeCache, true);
    } catch (Exception e) {
      log.warn("[ZookeeperRuleSource] NodeCache 注册失败: {}", e.getMessage());
    }
  }

  @Override
  public void destroy() throws Exception {
    if (client != null) {
      try {
        client.getClass().getMethod("close").invoke(client);
        log.info("[ZookeeperRuleSource] 连接已关闭");
      } catch (Exception e) {
        log.debug("[ZookeeperRuleSource] 关闭异常: {}", e.getMessage());
      }
    }
  }

  private List<RuleDefinition> parseRulesFromJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return YdszJson.parseArray(json, RuleDefinition.class);
    } catch (Exception e) {
      log.error("[ZookeeperRuleSource] JSON 解析失败: {}", e.getMessage());
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
