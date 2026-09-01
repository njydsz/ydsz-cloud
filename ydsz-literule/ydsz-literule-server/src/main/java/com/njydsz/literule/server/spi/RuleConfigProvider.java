package com.njydsz.literule.server.spi;

import java.util.List;
import java.util.function.Consumer;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleEnvironment;

/**
 * 规则配置提供者接口（SPI）
 *
 * <p>由消费方（如 execution 模块）提供实现，从数据库/配置中心加载规则定义。 literule 模块本身不依赖任何持久层实现，通过此接口反转依赖。
 *
 * <p>合并了原 {@code RuleSource} 的 Watch 监听能力，支持配置中心推送变更通知。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public interface RuleConfigProvider {

  /**
   * 加载全部启用的规则定义
   *
   * @return 启用的规则定义列表
   */
  List<RuleDefinitionDTO> loadEnabledRules();

  /**
   * 加载全部规则定义（含禁用）
   *
   * @return 全部规则定义列表
   */
  List<RuleDefinitionDTO> loadAllRules();

  /**
   * 保存规则定义（新增或更新）
   *
   * @param definition 规则定义
   * @param operator 操作人
   * @return 保存后的规则定义（含版本号）
   */
  RuleDefinitionDTO save(RuleDefinitionDTO definition, String operator);

  /**
   * 切换规则启停状态
   *
   * @param ruleCode 规则编码
   * @param enabled 是否启用
   * @param operator 操作人
   */
  void toggleEnabled(String ruleCode, boolean enabled, String operator);

  /**
   * 根据编码查询单条规则定义
   *
   * @param ruleCode 规则编码
   * @return 规则定义；不存在返回 null
   */
  RuleDefinitionDTO findByCode(String ruleCode);

  /**
   * 加载指定租户下全部启用的规则定义（1.5.1 起支持物理隔离）
   *
   * <p>默认实现调用 {@link #loadEnabledRules()} 后在内存按 tenantId 过滤， 性能敏感场景应覆写为带 {@code WHERE tenant_id =
   * ?} 的 SQL 查询。
   *
   * @param tenantId 租户 ID
   * @return 该租户下启用的规则定义列表
   * @since 26.09.01
   */
  default List<RuleDefinitionDTO> loadEnabledRulesByTenant(String tenantId) {
    List<RuleDefinitionDTO> all = loadEnabledRules();
    if (tenantId == null || tenantId.isBlank()) {
      return all;
    }
    return all.stream().filter(r -> tenantId.equals(r.getTenantId())).toList();
  }

  /**
   * 加载指定租户下全部规则定义（含禁用，1.5.1 起支持物理隔离）
   *
   * <p>默认实现调用 {@link #loadAllRules()} 后在内存按 tenantId 过滤。
   *
   * @param tenantId 租户 ID
   * @return 该租户下全部规则定义列表
   * @since 26.09.01
   */
  default List<RuleDefinitionDTO> loadAllRulesByTenant(String tenantId) {
    List<RuleDefinitionDTO> all = loadAllRules();
    if (tenantId == null || tenantId.isBlank()) {
      return all;
    }
    return all.stream().filter(r -> tenantId.equals(r.getTenantId())).toList();
  }

  /**
   * 加载指定租户和环境下启用的规则定义（1.6.0 起，P1-5 多环境隔离）
   *
   * <p>默认实现调用 {@link #loadEnabledRulesByTenant(String)} 后在内存按 environment 过滤：
   *
   * <ul>
   *   <li>规则的 environment 为 {@link RuleEnvironment#DEFAULT "default"} 时，匹配任何环境（向后兼容）
   *   <li>规则的 environment 非 "default" 时，必须与 {@code environment} 完全匹配
   * </ul>
   *
   * 性能敏感场景应覆写为带 {@code WHERE tenant_id = ? AND (environment = 'default' OR environment = ?)} 的 SQL
   * 查询。
   *
   * @param tenantId 租户 ID
   * @param environment 环境标识（dev/staging/prod/default）
   * @return 该租户下匹配环境的启用规则定义列表
   * @since 26.09.01
   */
  default List<RuleDefinitionDTO> loadEnabledRulesByEnv(String tenantId, String environment) {
    List<RuleDefinitionDTO> all = loadEnabledRulesByTenant(tenantId);
    if (environment == null
        || environment.isBlank()
        || RuleEnvironment.DEFAULT.equals(environment)) {
      return all;
    }
    return all.stream()
        .filter(
            r -> {
              String ruleEnv = r.getEnvironment();
              return ruleEnv == null
                  || ruleEnv.isBlank()
                  || RuleEnvironment.DEFAULT.equals(ruleEnv)
                  || environment.equals(ruleEnv);
            })
        .toList();
  }

  // ==================== Watch 监听能力（原 RuleSource） ====================

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
  default SourceType getType() {
    return SourceType.DB;
  }

  /**
   * 注册规则变更监听器
   *
   * <p>当配置中心的规则发生变更时，调用 {@code listener} 回调通知。对于不支持监听的数据源（如 DB），此方法为 no-op。
   *
   * @param listener 变更监听器，接收变更后的规则定义列表
   */
  default void addChangeListener(Consumer<List<RuleDefinitionDTO>> listener) {
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
