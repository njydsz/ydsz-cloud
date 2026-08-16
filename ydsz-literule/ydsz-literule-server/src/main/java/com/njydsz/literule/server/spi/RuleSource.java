package com.njydsz.literule.server.spi;

import com.njydsz.literule.api.RuleDefinition;
import java.util.List;
import java.util.function.Consumer;

/**
 * 规则数据源接口（P1-5 多数据源支持）
 *
 * <p>抽象规则配置的来源，支持从多种数据源加载和监听规则变更。 与 {@link RuleConfigProvider} 的区别：
 *
 * <ul>
 *   <li>{@code RuleConfigProvider} 是单向加载接口（拉取），不包含监听能力
 *   <li>{@code RuleSource} 是双向接口（拉取 + 监听），支持配置中心 Watch 推送
 * </ul>
 *
 * <p>已实现的适配器：
 *
 * <ul>
 *   <li>{@link DbRuleSource} - 数据库数据源（默认，基于 RuleConfigProvider 代理）
 *   <li>{@link NacosRuleSource} - Nacos 配置中心数据源
 *   <li>{@link ApolloRuleSource} - Apollo 配置中心数据源
 *   <li>{@link ZookeeperRuleSource} - ZooKeeper 数据源
 *   <li>{@link RedisRuleSource} - Redis 数据源
 *   <li>{@link FileRuleSource} - 文件数据源（YAML/JSON，GitOps 场景）
 * </ul>
 *
 * <p>参考 LiteFlow 的多数据源设计，支持 7 种数据源无缝切换。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface RuleSource {

  /** 数据源类型 */
  enum SourceType {
    /** 数据库 */
    DB,
    /** Nacos 配置中心 */
    NACOS,
    /** Apollo 配置中心 */
    APOLLO,
    /** ZooKeeper */
    ZOOKEEPER,
    /** Redis */
    REDIS,
    /** 文件（YAML/JSON） */
    FILE,
    /** 自定义 */
    CUSTOM
  }

  /**
   * 获取数据源类型
   *
   * @return 数据源类型
   */
  SourceType getType();

  /**
   * 加载全部启用的规则定义
   *
   * @return 启用的规则定义列表
   */
  List<RuleDefinition> loadEnabledRules();

  /**
   * 注册规则变更监听器
   *
   * <p>当配置中心的规则发生变更时，调用 {@code listener} 回调通知。 对于不支持监听的数据源（如 DB），此方法为 no-op。
   *
   * @param listener 变更监听器，接收变更后的规则定义列表
   */
  default void addChangeListener(Consumer<List<RuleDefinition>> listener) {
    // 默认不支持监听，子类按需实现
  }

  /**
   * 初始化数据源连接
   *
   * <p>在 Bean 初始化后调用，用于建立与配置中心的连接、注册监听器等。
   *
   * @throws Exception 初始化失败
   */
  default void init() throws Exception {
    // 默认无操作
  }

  /**
   * 销毁数据源连接
   *
   * <p>在 Bean 销毁前调用，释放连接、取消监听器等。
   *
   * @throws Exception 销毁失败
   */
  default void destroy() throws Exception {
    // 默认无操作
  }

  /**
   * 是否支持变更监听
   *
   * @return true=支持 Watch 推送（如 Nacos/ZK/Apollo）；false=仅支持轮询拉取
   */
  default boolean supportsWatch() {
    return false;
  }

  /**
   * 是否可用
   *
   * @return true=数据源已连接且可正常工作
   */
  default boolean isAvailable() {
    return true;
  }
}
