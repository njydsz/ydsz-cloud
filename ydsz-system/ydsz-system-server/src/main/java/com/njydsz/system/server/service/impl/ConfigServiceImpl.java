package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.ConfigVersionService;

/**
 * 系统配置 Service 实现
 *
 * <p>对 {@link ConfigService} 接口的完整实现，是「系统配置中心」的核心业务逻辑层。 集成 ydsz-common-cache 本地缓存（Spring Cache
 * 注解驱动）、Micrometer 指标和配置变更事件总线。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@code page} / {@code getById} / {@code save} / {@code updateById} / {@code
 *       removeById}， 全部走 {@code @Transactional} 事务保证
 *   <li><b>缓存读</b>：{@code getConfigValue}（单 key） / {@code getConfigsByGroup}（组批量） — 走
 *       ydsz-common-cache 本地缓存（Spring Cache 注解驱动）
 *   <li><b>公开配置</b>：{@code listPublicConfigs} — 前端「公开配置」接口数据源
 *   <li><b>缓存穿透防护</b>：ydsz-common-cache 内置 null 值缓存能力（allowNullValues=true）
 *   <li><b>变更广播</b>：通过 {@link DomainEventPublisher} 将 {@code CONFIG_CHANGED} 事件写入 Outbox 表， 与
 *       config 表写入共享同一事务（事务提交后由 {@code OutboxProcessor} 异步投递）， 订阅者可监听 {@code
 *       ydsz.workflow.sla-default-hours} 等关键配置变更
 *   <li><b>版本快照</b>：通过 {@link ConfigVersionService} 在写操作（{@code updateById} / {@code
 *       removeById}）时记录变更前快照， 支持版本回滚与变更审计
 *   <li><b>搜索同步</b>：通过 {@link SearchIndexEventBridge} 同步配置变更到 ES 索引
 *   <li><b>指标埋点</b>：通过 {@link com.njydsz.system.server.metrics.SystemMetrics} 暴露 Prometheus 指标
 * </ul>
 *
 * <p><b>缓存设计：</b>
 *
 * <ul>
 *   <li>缓存名称：{@link CacheConstants#SYSTEM_CONFIG_CACHE}（ydsz-common-cache 本地缓存）
 *   <li>单 key 缓存键：{@code value:{tenantId}:{configKey}}
 *   <li>组批量缓存键：{@code group:{tenantId}:{configGroup}}
 *   <li>公开配置缓存键：{@code public:{tenantId}}
 *   <li>TTL 与容量通过 {@code ydsz.cache.caches.system:config} YAML 配置
 *   <li>写操作触发 {@code @CacheEvict(allEntries=true)} 主动失效
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}； 读方法不开启事务，依赖 MyBatis
 * 自动提交。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ConfigService 配置 Service 接口
 * @see com.njydsz.system.domain.entity.Config 系统配置实体
 * @see com.njydsz.system.domain.enums.ConfigValueType 值类型枚举
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

  // 配置值校验常量
  private static final int MAX_STRING_LENGTH = 4096;
  private static final int MAX_JSON_LENGTH = 65536;
  private static final double MIN_NUMBER = -1e15;
  private static final double MAX_NUMBER = 1e15;
  private static final Pattern BOOLEAN_PATTERN =
      Pattern.compile("^(true|false|TRUE|FALSE|True|False)$");

  // ============================== 依赖注入 ==============================

  /** 系统配置仓储 */
  private final ConfigRepository configRepository;

  /** 系统监控指标采集器 */
  private final SystemMetrics metrics;

  /** 系统配置属性 */
  private final SystemProperties properties;

  /** 统一领域事件发布门面 */
  private final DomainEventPublisher eventPublisher;

  /** 配置版本服务（写操作时创建版本快照） */
  private final ConfigVersionService configVersionService;

  private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

  // ============================== CRUD ==============================

  @Override
  public PageResponse<List<ConfigVO>> page(ConfigPageQuery query) {
    QueryWrapper<Config> wrapper = buildQueryWrapper(query);
    Page<Config> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
    IPage<Config> result = configRepository.getConfigMapper().selectPage(mpPage, wrapper);
    return PageResponses.success(result, SystemConverter.INSTANT::entityToVO);
  }

  @Override
  public ConfigVO getById(String id) {
    Config entity = configRepository.getConfigMapper().selectById(id);
    return SystemConverter.INSTANT.entityToVO(entity);
  }

  @Override
  @CacheEvict(value = CacheConstants.SYSTEM_CONFIG_CACHE, allEntries = true)
  @Transactional(rollbackFor = Exception.class)
  public String save(ConfigDTO dto) {
    Config entity = toEntity(dto);
    validateValueType(entity.getValueType());
    checkDuplicateKey(entity);
    validateConfigValue(entity.getConfigKey(), entity.getConfigValue(), entity.getValueType());
    // 版本快照：新建配置无需快照（变更前不存在）
    configRepository.getConfigMapper().insert(entity);
    publishConfigChangedEvent(entity.getConfigKey(), entity.getConfigGroup());
    indexUpsert(entity);
    return entity.getId();
  }

  @Override
  @CacheEvict(value = CacheConstants.SYSTEM_CONFIG_CACHE, allEntries = true)
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(ConfigDTO dto) {
    Config entity = toEntity(dto);
    validateValueType(entity.getValueType());
    validateConfigValue(entity.getConfigKey(), entity.getConfigValue(), entity.getValueType());
    // 版本快照：查询变更前状态
    Config before =
        configRepository
            .getConfigMapper()
            .selectOne(
                new QueryWrapper<Config>()
                    .eq("config_key", entity.getConfigKey())
                    .eq("deleted", 0));
    String snapshotJson = before != null ? YdszJson.toJson(before) : null;
    boolean updated = configRepository.getConfigMapper().updateById(entity) > 0;
    if (updated) {
      // 创建版本快照（与配置变更同一事务）
      configVersionService.createVersion(
          entity.getConfigKey(),
          entity.getConfigGroup(),
          "v" + System.currentTimeMillis(),
          "更新配置: " + entity.getConfigKey(),
          snapshotJson);
      publishConfigChangedEvent(entity.getConfigKey(), entity.getConfigGroup());
      indexUpsert(entity);
    }
    return updated;
  }

  @Override
  @CacheEvict(value = CacheConstants.SYSTEM_CONFIG_CACHE, allEntries = true)
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    Config entity = configRepository.getConfigMapper().selectById(id);
    // 版本快照：查询变更前状态
    String snapshotJson = entity != null ? YdszJson.toJson(entity) : null;
    boolean removed = configRepository.getConfigMapper().deleteById(id) > 0;
    if (removed && entity != null) {
      // 创建版本快照（与配置变更同一事务）
      configVersionService.createVersion(
          entity.getConfigKey(),
          entity.getConfigGroup(),
          "v" + System.currentTimeMillis(),
          "删除配置: " + entity.getConfigKey(),
          snapshotJson);
      publishConfigChangedEvent(entity.getConfigKey(), entity.getConfigGroup());
      indexDelete(id);
    }
    return removed;
  }

  /**
   * 同步配置变更到 ES 搜索索引（可选能力）。
   *
   * <p>通过 {@code ObjectProvider} 获取可选依赖 {@link SearchIndexEventBridge}， 仅当搜索模块存在时才执行索引
   * upsert，避免对未启用搜索的环境产生硬依赖。
   *
   * @param entity 待同步的配置实体
   */
  private void indexUpsert(Config entity) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("config", entity);
    }
  }

  /**
   * 从 ES 搜索索引删除配置文档（可选能力）。
   *
   * <p>同样走 {@code ObjectProvider} 可选依赖，未启用搜索模块时静默跳过。
   *
   * @param id 待删除的配置 ID
   */
  private void indexDelete(String id) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexDelete("config", id);
    }
  }

  // ============================== 业务查询 ==============================

  @Override
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configValue(#p0)")
  public String getConfigValue(String configKey) {
    long start = System.nanoTime();
    try {
      metrics.recordConfigCacheMiss();
      Config config = configRepository.getConfigMapper().selectByConfigKey(configKey);
      return config != null ? config.getConfigValue() : null;
    } finally {
      metrics.recordConfigRead(System.nanoTime() - start);
    }
  }

  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "created_by")
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configGroup(#p0)")
  public List<ConfigVO> getConfigsByGroup(String configGroup) {
    long start = System.nanoTime();
    try {
      metrics.recordConfigCacheMiss();
      QueryWrapper<Config> wrapper = new QueryWrapper<>();
      wrapper.eq("config_group", configGroup).eq("status", "ENABLED").orderByAsc("sort_order");
      return configRepository.getConfigMapper().selectList(wrapper).stream()
          .map(SystemConverter.INSTANT::entityToVO)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } finally {
      metrics.recordConfigRead(System.nanoTime() - start);
    }
  }

  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "created_by")
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configPublic()")
  public List<ConfigVO> listPublicConfigs() {
    long start = System.nanoTime();
    try {
      metrics.recordConfigCacheMiss();
      QueryWrapper<Config> wrapper = new QueryWrapper<>();
      wrapper.eq("is_public", 1).eq("status", "ENABLED").orderByAsc("sort_order");
      return configRepository.getConfigMapper().selectList(wrapper).stream()
          .map(SystemConverter.INSTANT::entityToVO)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } finally {
      metrics.recordConfigRead(System.nanoTime() - start);
    }
  }

  // ============================== 私有方法 ==============================

  /**
   * 根据分页查询条件构造 MyBatis-Plus 查询包装器。
   *
   * <p>支持分组精确匹配、配置键模糊匹配、状态精确匹配；默认按创建时间倒序。
   *
   * @param query 分页查询条件
   * @return 构造好的查询包装器
   */
  private QueryWrapper<Config> buildQueryWrapper(ConfigPageQuery query) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    if (query.getConfigGroup() != null && !query.getConfigGroup().isBlank()) {
      wrapper.eq("config_group", query.getConfigGroup());
    }
    if (query.getConfigKey() != null && !query.getConfigKey().isBlank()) {
      wrapper.like("config_key", query.getConfigKey());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.orderByDesc("created_at");
    return wrapper;
  }

  /**
   * 将配置 DTO 转换为持久化实体。
   *
   * <p>未显式指定状态时默认置为 {@code ENABLED}，保证新建配置默认可用。
   *
   * @param dto 配置 DTO（为 null 时返回 null）
   * @return 转换后的实体
   */
  private Config toEntity(ConfigDTO dto) {
    if (dto == null) {
      return null;
    }
    Config entity = new Config();
    entity.setId(dto.getId());
    entity.setConfigGroup(dto.getConfigGroup());
    entity.setConfigKey(dto.getConfigKey());
    entity.setConfigValue(dto.getConfigValue());
    entity.setValueType(dto.getValueType());
    entity.setDefaultValue(dto.getDefaultValue());
    entity.setDescription(dto.getDescription());
    entity.setIsPublic(dto.getIsPublic());
    entity.setSortOrder(dto.getSortOrder());
    entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
    return entity;
  }

  /**
   * 校验配置值类型合法性。
   *
   * <p>委托 {@link ConfigValueType#validate} 完成，非法类型将抛出 {@link BusinessException}
   *（{@link SystemExceptionCode#CONFIG_VALUE_TYPE_INVALID}）阻止脏数据落库。
   *
   * @param valueType 值类型字符串
   */
  private void validateValueType(String valueType) {
    try {
      ConfigValueType.validate(valueType);
    } catch (IllegalArgumentException e) {
      throw BusinessException.of(SystemExceptionCode.CONFIG_VALUE_TYPE_INVALID)
          .data("valueType", valueType);
    }
  }

  /**
   * 对配置值进行格式校验（告警模式，不阻止保存）。
   *
   * <p><b>向后兼容：</b>校验失败仅记录告警日志，不阻止配置保存， 保证现有非法配置值仍可继续使用，同时提示管理员修正。
   *
   * @param configKey 配置键（用于日志定位，为 null 时跳过）
   * @param configValue 配置值字符串（为 null 时跳过校验）
   * @param valueType 值类型字符串（为 null 或无法识别时跳过校验）
   */
  private void validateConfigValue(String configKey, String configValue, String valueType) {
    if (configKey == null || configValue == null || valueType == null) {
      return;
    }
    try {
      String error = validateValueByFormat(configValue, valueType);
      if (error != null) {
        log.warn(
            "[ConfigService] 配置值格式校验失败: configKey={}, valueType={}, value={}, error={}",
            configKey,
            valueType,
            abbreviate(configValue),
            error);
        if (metrics != null) {
          metrics.recordConfigValidationWarning();
        }
      }
    } catch (Exception e) {
      log.warn(
          "[ConfigService] 配置值校验异常（不影响保存）: configKey={}, valueType={}, error={}",
          configKey,
          valueType,
          e.getMessage());
    }
  }

  /**
   * 按值类型进行格式校验（内联实现，替代已移除的 Schema 校验引擎）。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>STRING — 长度 ≤ {@value #MAX_STRING_LENGTH}
   *   <li>INTEGER / NUMBER — 可解析为数值且在 [{}, {}] 范围内
   *   <li>BOOLEAN — 必须为 true/false
   *   <li>JSON — 必须为合法 JSON 且长度 ≤ {@value #MAX_JSON_LENGTH}
   * </ul>
   *
   * @return 错误描述，null 表示通过
   */
  private static String validateValueByFormat(String configValue, String valueType) {
    try {
      ConfigValueType type = ConfigValueType.valueOf(valueType.toUpperCase());
      return switch (type) {
        case STRING ->
            configValue.length() > MAX_STRING_LENGTH ? "字符串长度超过限制 " + MAX_STRING_LENGTH : null;
        case NUMBER -> {
          double v = Double.parseDouble(configValue.trim());
          if (v < MIN_NUMBER || v > MAX_NUMBER) {
            yield "数值超出范围 [" + MIN_NUMBER + ", " + MAX_NUMBER + "]";
          }
          yield null;
        }
        case BOOLEAN ->
            BOOLEAN_PATTERN.matcher(configValue.trim()).matches() ? null : "布尔值必须是 true/false";
        case JSON -> {
          if (configValue.length() > MAX_JSON_LENGTH) {
            yield "JSON 长度超过限制 " + MAX_JSON_LENGTH;
          }
          parseJsonLoose(configValue);
          yield null;
        }
      };
    } catch (NumberFormatException e) {
      return "数值格式非法";
    } catch (IllegalArgumentException e) {
      return "未知的值类型: " + valueType;
    } catch (Exception e) {
      return e.getMessage() != null ? e.getMessage() : "校验异常";
    }
  }

  /**
   * 宽松 JSON 校验：尝试解析为对象或数组，解析失败抛出异常。
   *
   * @param json JSON 字符串
   * @throws RuntimeException 解析失败时抛出
   */
  private static void parseJsonLoose(String json) {
    String trimmed = json.trim();
    if (trimmed.startsWith("{")) {
      YdszJson.parseMap(trimmed);
    } else if (trimmed.startsWith("[")) {
      YdszJson.parseArray(trimmed, Object.class);
    } else {
      throw new RuntimeException("不是合法的 JSON 对象或数组");
    }
  }

  /**
   * 截断字符串用于日志输出，避免超长值污染日志。
   *
   * @param value 原始字符串（可为 null）
   * @return 截断后的字符串（最长 128 字符）
   */
  private static String abbreviate(String value) {
    if (value == null) {
      return "null";
    }
    if (value.length() <= 128) {
      return value;
    }
    return value.substring(0, 128) + "...(truncated, len=" + value.length() + ")";
  }

  /**
   * 校验同一分组下配置键是否重复。
   *
   * <p>写入前按 {@code (configGroup, configKey)} 唯一性预检， 命中已有记录时抛出 {@link
   * IllegalArgumentException}，避免唯一索引冲突导致写入失败。
   *
   * @param entity 待校验的配置实体
   */
  private void checkDuplicateKey(Config entity) {
    QueryWrapper<Config> checkWrapper = new QueryWrapper<>();
    checkWrapper
        .eq("config_group", entity.getConfigGroup())
        .eq("config_key", entity.getConfigKey());
    if (configRepository.getConfigMapper().selectCount(checkWrapper) > 0) {
      throw BusinessException.of(SystemExceptionCode.CONFIG_KEY_DUPLICATE)
          .data("configGroup", entity.getConfigGroup())
          .data("configKey", entity.getConfigKey());
    }
  }

  /**
   * 广播配置变更事件（用于跨实例本地缓存失效感知）。
   *
   * <p>通过可选的 {@code OutboxService} 追加 {@code CONFIG_CHANGED} 事件， 订阅方（如其他实例的 {@code
   * CrossModuleEventListener}）接收事件后清除本地缓存。 事件发布失败仅告警，不影响主流程。
   *
   * @param configKey 配置键（为 null 时跳过）
   * @param configGroup 配置分组（为 null 时跳过）
   */
  private void publishConfigChangedEvent(String configKey, String configGroup) {
    eventPublisher.publish(
        DomainEvent.builder()
            .aggregateType("Config")
            .aggregateId(configKey)
            .eventType(DomainEventTypes.CONFIG_CHANGED)
            .metadata("configKey", configKey)
            .metadata("configGroup", configGroup)
            .build());
  }
}
