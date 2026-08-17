package com.njydsz.system.server.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.EntityVersionCreateDTO;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigExcelVO;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.CursorPageResponse;
import com.njydsz.system.domain.vo.ImportResult;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.search.SearchIndexSyncer;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.util.SystemVersionUtils;

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
 *   <li><b>版本快照</b>：通过 {@link EntityVersionService} 在写操作（{@code updateById} / {@code
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

  /** 游标分页页大小上限 */
  private static final int MAX_CURSOR_PAGE_SIZE = 500;

  /** 日志截断最大长度 */
  private static final int MAX_LOG_ABBREVIATE_LENGTH = 128;

  // ============================== 依赖注入 ==============================

  /** 系统配置仓储 */
  private final ConfigRepository configRepository;

  /** 系统监控指标采集器 */
  private final SystemMetrics metrics;

  /** 系统配置属性 */
  private final SystemProperties properties;

  /** 统一领域事件发布门面 */
  private final DomainEventPublisher eventPublisher;

  /** 统一实体版本服务（写操作时创建版本快照） */
  private final EntityVersionService entityVersionService;

  /** Spring Cache 管理器（用于按 key 精准失效缓存，替代 allEntries 全量清空） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器（SpEL 与手动 evict 共用） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** 搜索索引同步器（可选能力，未启用搜索模块时静默跳过） */
  private final SearchIndexSyncer searchIndexSyncer;

  /** Excel 导出辅助类（用于配置导入导出） */
  private final ExcelExportHelper excelExportHelper;

  // ============================== CRUD ==============================

  @Override
  public PageResponse<List<ConfigVO>> page(ConfigPageQuery query) {
    QueryWrapper<Config> wrapper = buildQueryWrapper(query);
    Page<Config> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
    IPage<Config> result = configRepository.getConfigMapper().selectPage(mpPage, wrapper);
    return PageResponses.success(result, SystemConverter.INSTANT::entityToVO);
  }

  @Override
  public CursorPageResponse<ConfigVO> pageByCursor(
      String configGroup, String configKey, int pageSize, String cursor) {
    // 1. 校验并归一化页大小
    int safePageSize = Math.min(Math.max(pageSize, 1), MAX_CURSOR_PAGE_SIZE);

    // 2. 构建查询条件（seek method：ID > cursor）
    QueryWrapper<Config> wrapper = buildCursorWrapper(configGroup, configKey, cursor);
    wrapper.orderByAsc("id");
    wrapper.last("LIMIT " + safePageSize);

    // 3. 查询数据
    List<Config> entities = configRepository.getConfigMapper().selectList(wrapper);
    List<ConfigVO> records =
        entities.stream()
            .map(SystemConverter.INSTANT::entityToVO)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    // 4. 计算下一页游标（本页满且存在后续数据时）
    String nextCursor = null;
    if (records.size() == safePageSize) {
      Config lastEntity = entities.get(entities.size() - 1);
      if (hasMoreData(configGroup, configKey, lastEntity.getId())) {
        nextCursor = lastEntity.getId();
      }
    }

    return CursorPageResponse.of(records, nextCursor);
  }

  /**
   * 构建游标分页查询条件（私有）。
   *
   * @param configGroup 配置分组（可选）
   * @param configKey 配置键（可选，模糊）
   * @param cursor 上一页游标（可选）
   * @return 查询包装器（含 deleted=0 与过滤条件）
   */
  private QueryWrapper<Config> buildCursorWrapper(
      String configGroup, String configKey, String cursor) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq("config_group", configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like("config_key", configKey);
    }
    if (cursor != null && !cursor.isBlank()) {
      wrapper.gt("id", cursor);
    }
    return wrapper;
  }

  /**
   * 判断游标之后是否还有更多数据（私有）。
   *
   * @param configGroup 配置分组（可选）
   * @param configKey 配置键（可选）
   * @param lastId 本页最后一条记录 ID
   * @return 存在后续数据返回 {@code true}
   */
  private boolean hasMoreData(String configGroup, String configKey, String lastId) {
    QueryWrapper<Config> countWrapper = buildCursorWrapper(configGroup, configKey, lastId);
    Long moreCount = configRepository.getConfigMapper().selectCount(countWrapper);
    return moreCount != null && moreCount > 0;
  }

  @Override
  public ConfigVO getById(String id) {
    Config entity = configRepository.getConfigMapper().selectById(id);
    return SystemConverter.INSTANT.entityToVO(entity);
  }

  @Override
  @Caching(
      evict = {
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configValue(#vo.configKey)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configGroup(#vo.configGroup)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configPublic()")
      })
  @Transactional(rollbackFor = Exception.class)
  public String save(ConfigVO vo) {
    Config entity = toEntity(vo);
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
  @Caching(
      evict = {
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configValue(#vo.configKey)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configGroup(#vo.configGroup)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configPublic()")
      })
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(ConfigVO vo) {
    Config entity = toEntity(vo);
    validateValueType(entity.getValueType());
    validateConfigValue(entity.getConfigKey(), entity.getConfigValue(), entity.getValueType());
    // 版本快照：查询变更前状态
    Config before = configRepository.selectByKeyIgnoreStatus(entity.getConfigKey());
    String snapshotJson = before != null ? YdszJson.toJson(before) : null;
    boolean updated = configRepository.getConfigMapper().updateById(entity) > 0;
    if (updated) {
      // 分组变更时旧分组缓存一并失效
      if (before != null && !Objects.equals(before.getConfigGroup(), entity.getConfigGroup())) {
        evictConfigGroup(before.getConfigGroup());
      }
      // 创建版本快照（与配置变更同一事务）
      entityVersionService.createVersion(
          EntityVersionCreateDTO.builder()
              .resourceType(EntityVersionService.RESOURCE_TYPE_CONFIG)
              .resourceKey(entity.getConfigKey())
              .resourceGroup(entity.getConfigGroup())
              .version(SystemVersionUtils.nextVersion())
              .changeLog("更新配置: " + entity.getConfigKey())
              .snapshotJson(snapshotJson)
              .build());
      publishConfigChangedEvent(entity.getConfigKey(), entity.getConfigGroup());
      indexUpsert(entity);
    }
    return updated;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    Config entity = configRepository.getConfigMapper().selectById(id);
    // 版本快照：查询变更前状态
    String snapshotJson = entity != null ? YdszJson.toJson(entity) : null;
    boolean removed = configRepository.getConfigMapper().deleteById(id) > 0;
    if (removed && entity != null) {
      // 精准失效单 key / 分组 / 公开配置缓存
      evictConfigCaches(entity.getConfigKey(), entity.getConfigGroup());
      // 创建版本快照（与配置变更同一事务）
      entityVersionService.createVersion(
          EntityVersionCreateDTO.builder()
              .resourceType(EntityVersionService.RESOURCE_TYPE_CONFIG)
              .resourceKey(entity.getConfigKey())
              .resourceGroup(entity.getConfigGroup())
              .version(SystemVersionUtils.nextVersion())
              .changeLog("删除配置: " + entity.getConfigKey())
              .snapshotJson(snapshotJson)
              .build());
      publishConfigChangedEvent(entity.getConfigKey(), entity.getConfigGroup());
      indexDelete(id);
    }
    return removed;
  }

  /**
   * 同步配置变更到 ES 搜索索引（可选能力）。
   *
   * <p>委托 {@link SearchIndexSyncer}，仅当搜索模块存在时才执行索引 upsert，避免对未启用搜索的环境产生硬依赖。
   *
   * @param entity 待同步的配置实体
   */
  private void indexUpsert(Config entity) {
    searchIndexSyncer.upsert("config", entity);
  }

  /**
   * 从 ES 搜索索引删除配置文档（可选能力）。
   *
   * <p>同样委托 {@link SearchIndexSyncer}，未启用搜索模块时静默跳过。
   *
   * @param id 待删除的配置 ID
   */
  private void indexDelete(String id) {
    searchIndexSyncer.delete("config", id);
  }

  /**
   * 按配置键精准失效缓存（单 key + 公开配置）。
   *
   * <p>用于写操作/回滚后定向清除，避免 {@code allEntries=true} 全量清空导致的缓存击穿。
   *
   * @param configKey 配置键
   * @param configGroup 配置分组（为 null 时跳过分组缓存）
   */
  private void evictConfigCaches(String configKey, String configGroup) {
    evictConfigKey(configKey);
    evictConfigGroup(configGroup);
    evictConfigPublic();
  }

  /** 失效「按配置键查询」缓存。 */
  private void evictConfigKey(String configKey) {
    if (configKey == null) {
      return;
    }
    cacheManager
        .getCache(CacheConstants.SYSTEM_CONFIG_CACHE)
        .evict(cacheKeyBuilder.configValue(configKey));
  }

  /** 失效「按分组批量查询」缓存。 */
  private void evictConfigGroup(String configGroup) {
    if (configGroup == null) {
      return;
    }
    cacheManager
        .getCache(CacheConstants.SYSTEM_CONFIG_CACHE)
        .evict(cacheKeyBuilder.configGroup(configGroup));
  }

  /** 失效「公开配置」缓存。 */
  private void evictConfigPublic() {
    cacheManager
        .getCache(CacheConstants.SYSTEM_CONFIG_CACHE)
        .evict(cacheKeyBuilder.configPublic());
  }

  // ============================== 业务查询 ==============================

  @Override
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configValue(#p0)")
  public String getConfigValue(String configKey) {
    long start = System.nanoTime();
    try {
      metrics.recordConfigCacheMiss();
      Config config = configRepository.selectEnabledByKey(configKey);
      return config != null ? config.getConfigValue() : null;
    } finally {
      metrics.recordConfigRead(System.nanoTime() - start);
    }
  }

  @Override
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configGroup(#p0)")
  public List<ConfigVO> getConfigsByGroup(String configGroup) {
    long start = System.nanoTime();
    try {
      metrics.recordConfigCacheMiss();
      return configRepository.selectEnabledByGroup(configGroup).stream()
          .map(SystemConverter.INSTANT::entityToVO)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } finally {
      metrics.recordConfigRead(System.nanoTime() - start);
    }
  }

  @Override
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configPublic()")
  public List<ConfigVO> listPublicConfigs() {
    long start = System.nanoTime();
    try {
      metrics.recordConfigCacheMiss();
      return configRepository.selectPublicEnabled().stream()
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
  private Config toEntity(ConfigVO vo) {
    if (vo == null) {
      return null;
    }
    Config entity = new Config();
    entity.setId(vo.getId());
    entity.setConfigGroup(vo.getConfigGroup());
    entity.setConfigKey(vo.getConfigKey());
    entity.setConfigValue(vo.getConfigValue());
    entity.setValueType(vo.getValueType());
    entity.setDefaultValue(vo.getDefaultValue());
    entity.setDescription(vo.getDescription());
    entity.setIsPublic(vo.getIsPublic());
    entity.setSortOrder(vo.getSortOrder());
    entity.setStatus(vo.getStatus() != null ? vo.getStatus() : "ENABLED");
    return entity;
  }

  /**
   * 校验配置值类型合法性。
   *
   * <p>委托 {@link ConfigValueType#validate} 完成，非法类型将抛出 {@link BusinessException}
   *（{@link SystemExceptionCode#VALUE_TYPE_INVALID}）阻止脏数据落库。
   *
   * @param valueType 值类型字符串
   */
  private void validateValueType(String valueType) {
    try {
      ConfigValueType.validate(valueType);
    } catch (IllegalArgumentException e) {
      throw BusinessException.of(SystemExceptionCode.VALUE_TYPE_INVALID)
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
        case STRING -> validateStringValue(configValue);
        case NUMBER -> validateNumberValue(configValue);
        case BOOLEAN -> validateBooleanValue(configValue);
        case JSON -> validateJsonValue(configValue);
      };
    } catch (NumberFormatException e) {
      return "数值格式非法";
    } catch (IllegalArgumentException e) {
      return "未知的值类型: " + valueType;
    } catch (Exception e) {
      return e.getMessage() != null ? e.getMessage() : "校验异常";
    }
  }

  /** 校验 STRING 类型长度（私有）。 */
  private static String validateStringValue(String configValue) {
    return configValue.length() > MAX_STRING_LENGTH
        ? "字符串长度超过限制 " + MAX_STRING_LENGTH
        : null;
  }

  /** 校验 NUMBER 类型可解析性与范围（私有）。 */
  private static String validateNumberValue(String configValue) {
    double v = Double.parseDouble(configValue.trim());
    if (v < MIN_NUMBER || v > MAX_NUMBER) {
      return "数值超出范围 [" + MIN_NUMBER + ", " + MAX_NUMBER + "]";
    }
    return null;
  }

  /** 校验 BOOLEAN 类型取值（私有）。 */
  private static String validateBooleanValue(String configValue) {
    return BOOLEAN_PATTERN.matcher(configValue.trim()).matches()
        ? null
        : "布尔值必须是 true/false";
  }

  /** 校验 JSON 类型合法性与长度（私有）。 */
  private static String validateJsonValue(String configValue) {
    if (configValue.length() > MAX_JSON_LENGTH) {
      return "JSON 长度超过限制 " + MAX_JSON_LENGTH;
    }
    parseJsonLoose(configValue);
    return null;
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
      throw BusinessException.of(SystemExceptionCode.VALUE_TYPE_INVALID)
          .data("reason", "JSON 类型值必须以 '{' 或 '[' 开头");
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
    if (value.length() <= MAX_LOG_ABBREVIATE_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_LOG_ABBREVIATE_LENGTH)
        + "...(truncated, len="
        + value.length()
        + ")";
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
    if (configRepository.existsByGroupAndKey(entity.getConfigGroup(), entity.getConfigKey())) {
      throw BusinessException.of(SystemExceptionCode.CONFIG_KEY_DUPLICATE)
          .data("configGroup", entity.getConfigGroup())
          .data("configKey", entity.getConfigKey());
    }
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(String resourceKey, String targetVersion, String operatorId) {
    // 1. 执行回滚（通过 EntityVersionService 回调机制）
    return entityVersionService.rollbackTo(
        EntityVersionService.RESOURCE_TYPE_CONFIG,
        resourceKey,
        targetVersion,
        operatorId,
        snapshotJson -> {
          // 2. 反序列化快照并更新配置项
          String snapshotGroup = null;
          if (snapshotJson != null && !snapshotJson.isBlank()) {
            try {
              ConfigVO snapshotVO = YdszJson.fromJson(snapshotJson, ConfigVO.class);
              snapshotGroup = snapshotVO.getConfigGroup();
              Config currentConfig = configRepository.selectByKeyIgnoreStatus(resourceKey);
              if (currentConfig != null) {
                currentConfig.setConfigValue(snapshotVO.getConfigValue());
                currentConfig.setValueType(snapshotVO.getValueType());
                currentConfig.setDefaultValue(snapshotVO.getDefaultValue());
                currentConfig.setDescription(snapshotVO.getDescription());
                currentConfig.setIsPublic(snapshotVO.getIsPublic());
                currentConfig.setSortOrder(snapshotVO.getSortOrder());
                currentConfig.setStatus(snapshotVO.getStatus());
                configRepository.getConfigMapper().updateById(currentConfig);
              } else {
                Config newConfig = new Config();
                newConfig.setConfigGroup(snapshotVO.getConfigGroup());
                newConfig.setConfigKey(snapshotVO.getConfigKey());
                newConfig.setConfigValue(snapshotVO.getConfigValue());
                newConfig.setValueType(snapshotVO.getValueType());
                newConfig.setDefaultValue(snapshotVO.getDefaultValue());
                newConfig.setDescription(snapshotVO.getDescription());
                newConfig.setIsPublic(snapshotVO.getIsPublic());
                newConfig.setSortOrder(snapshotVO.getSortOrder());
                newConfig.setStatus(snapshotVO.getStatus());
                configRepository.getConfigMapper().insert(newConfig);
              }
            } catch (Exception e) {
              throw BusinessException.of(SystemExceptionCode.SNAPSHOT_PARSE_ERROR)
                  .data("reason", e.getMessage());
            }
          }
          // 3. 精准失效缓存（P0-2：回滚后必须清缓存，避免读到旧值）并发布变更事件
          evictConfigKey(resourceKey);
          if (snapshotGroup != null) {
            evictConfigGroup(snapshotGroup);
          }
          evictConfigPublic();
          publishConfigChangedEvent(resourceKey, snapshotGroup);
        });
  }

  // ============================== 导入导出 ==============================

  @Override
  public byte[] exportConfigs(String configGroup) {
    // 1. 查询配置数据（含分组过滤）
    List<Config> configs = loadConfigsForExport(configGroup);

    // 2. 转换为 Excel VO 并导出
    List<ConfigExcelVO> excelRows =
        configs.stream().map(this::toExcelVO).collect(Collectors.toList());
    return excelRows.isEmpty()
        ? new byte[0]
        : excelExportHelper.export("系统配置", ConfigExcelVO.class, excelRows);
  }

  /**
   * 加载导出配置数据（私有）。
   *
   * @param configGroup 配置分组（为空时导出全部）
   * @return 未删除配置列表（按分组/排序）
   */
  private List<Config> loadConfigsForExport(String configGroup) {
    com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Config> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Config>()
            .eq("deleted", 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq("config_group", configGroup).orderByAsc("sort_order");
    } else {
      wrapper.orderByAsc("config_group", "sort_order");
    }
    return configRepository.getConfigMapper().selectList(wrapper);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ImportResult importConfigs(InputStream inputStream) {
    // 1. 读取 Excel 文件
    List<ConfigExcelVO> excelRows = readExcel(inputStream);
    if (excelRows.isEmpty()) {
      return ImportResult.builder()
          .totalCount(0)
          .successCount(0)
          .failCount(0)
          .skipCount(0)
          .message("Excel 文件为空")
          .build();
    }

    // 2. 逐条校验并转换（必填 / 值类型 / DB 唯一性）
    List<String> errors = new ArrayList<>();
    List<ConfigVO> validItems = new ArrayList<>();
    int skipCount = 0;
    for (int i = 0; i < excelRows.size(); i++) {
      String error = validateExcelRow(excelRows.get(i), i + 2);
      if (error != null) {
        errors.add(error);
        skipCount++;
      } else {
        validItems.add(toConfigVO(excelRows.get(i)));
      }
    }

    // 3. 批量保存有效数据（使用 insertBatch 消除 N+1）
    int successCount = saveValidItemsBatch(validItems, errors);

    // 4. 构建导入结果
    return ImportResult.builder()
        .totalCount(excelRows.size())
        .successCount(successCount)
        .failCount(excelRows.size() - successCount - skipCount)
        .skipCount(skipCount)
        .errors(errors)
        .message(
            String.format(
                "导入完成: 成功 %d 条, 跳过 %d 条, 失败 %d 条",
                successCount, skipCount, excelRows.size() - successCount - skipCount))
        .build();
  }

  /**
   * 读取 Excel 配置数据（私有）。
   *
   * @param inputStream Excel 输入流
   * @return 配置 Excel 行列表
   */
  private List<ConfigExcelVO> readExcel(InputStream inputStream) {
    try {
      List<ConfigExcelVO> rows =
          ExcelFacade.read(inputStream, ConfigExcelVO.class).sheet(0).doReadAll();
      return rows != null ? rows : List.of();
    } catch (Exception e) {
      log.warn("[ConfigService] Excel 读取失败: {}", e.getMessage());
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "Excel 文件读取失败: " + e.getMessage());
    }
  }

  /**
   * 校验单条 Excel 行（私有）。
   *
   * <p>校验必填字段、值类型、DB 唯一性；通过返回 null，否则返回错误描述。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号（从 2 开始，第 1 行为表头）
   * @return 错误描述；校验通过返回 null
   */
  private String validateExcelRow(ConfigExcelVO excelRow, int rowNum) {
    String requiredError = validateRequiredFields(excelRow, rowNum);
    if (requiredError != null) {
      return requiredError;
    }
    String typeError = validateExcelValueType(excelRow, rowNum);
    if (typeError != null) {
      return typeError;
    }
    if (configRepository.existsByGroupAndKey(excelRow.getConfigGroup(), excelRow.getConfigKey())) {
      return "第 " + rowNum + " 行: 配置已存在("
          + excelRow.getConfigGroup() + "/" + excelRow.getConfigKey() + ")";
    }
    return null;
  }

  /**
   * 校验 Excel 行必填字段（私有）。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号
   * @return 错误描述；通过返回 null
   */
  private String validateRequiredFields(ConfigExcelVO excelRow, int rowNum) {
    if (excelRow.getConfigGroup() == null || excelRow.getConfigGroup().isBlank()) {
      return "第 " + rowNum + " 行: 配置分组不能为空";
    }
    if (excelRow.getConfigKey() == null || excelRow.getConfigKey().isBlank()) {
      return "第 " + rowNum + " 行: 配置键不能为空";
    }
    if (excelRow.getConfigValue() == null || excelRow.getConfigValue().isBlank()) {
      return "第 " + rowNum + " 行: 配置值不能为空";
    }
    return null;
  }

  /**
   * 校验 Excel 行值类型（私有）。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号
   * @return 错误描述；通过返回 null
   */
  private String validateExcelValueType(ConfigExcelVO excelRow, int rowNum) {
    if (excelRow.getValueType() == null || excelRow.getValueType().isBlank()) {
      return null;
    }
    try {
      ConfigValueType.validate(excelRow.getValueType());
      return null;
    } catch (IllegalArgumentException e) {
      return "第 " + rowNum + " 行: 值类型不合法: " + excelRow.getValueType();
    }
  }

  /**
   * Excel 行转换为配置 VO（私有）。
   *
   * @param excelRow Excel 行数据
   * @return 配置 VO
   */
  private ConfigVO toConfigVO(ConfigExcelVO excelRow) {
    ConfigVO vo = new ConfigVO();
    vo.setConfigGroup(excelRow.getConfigGroup());
    vo.setConfigKey(excelRow.getConfigKey());
    vo.setConfigValue(excelRow.getConfigValue());
    vo.setValueType(excelRow.getValueType());
    vo.setDefaultValue(excelRow.getDefaultValue());
    vo.setDescription(excelRow.getDescription());
    vo.setIsPublic(excelRow.getIsPublic());
    vo.setSortOrder(excelRow.getSortOrder());
    vo.setStatus(excelRow.getStatus());
    return vo;
  }

  /**
   * 批量保存有效配置（私有，使用 insertBatch 消除 N+1）。
   *
   * <p><b>P0-2 优化：</b>将 N 次单条 INSERT 合并为 1 次批量 INSERT，显著减少 DB 往返次数。 批量 XML 不走 MyBatis-Plus 拦截器（租户拦截器、审计字段自动填充均不生效），
   * 需在此处手动预生成 ID 并填充审计字段。
   *
   * @param validItems 校验通过的配置列表
   * @param errors 错误收集器（保存失败时追加）
   * @return 保存成功条数
   */
  private int saveValidItemsBatch(List<ConfigVO> validItems, List<String> errors) {
    if (validItems.isEmpty()) {
      return 0;
    }
    try {
      // 1. DTO 转 Entity，预生成 ID + 填充审计字段
      List<Config> entities = validItems.stream()
          .map(this::toEntityWithIdForImport)
          .collect(Collectors.toList());

      // 2. 批量插入（1 次 SQL 完成全部写入）
      configRepository.getConfigMapper().insertBatch(entities);

      // 3. 精准失效缓存：按涉及 configGroup 逐一失效
      entities.stream()
          .map(Config::getConfigGroup)
          .distinct()
          .forEach(this::evictConfigGroup);
      evictConfigPublic();

      // 4. 同步搜索索引 + 发布变更事件（异步，不阻塞主流程）
      entities.forEach(entity -> indexUpsert(entity));

      return entities.size();
    } catch (Exception e) {
      errors.add("批量导入失败: " + e.getMessage());
      return 0;
    }
  }

  /**
   * DTO 转 Entity + 预生成雪花 ID + 审计字段（导入场景专用，私有）。
   *
   * <p>批量 XML 插入不走 MyBatis-Plus 拦截器（CombinedFieldFillInterceptor、租户拦截器、 IdentifierGenerator
   * 均不生效），需在此处手动预生成 ID 并填充审计字段。
   *
   * <p>缺省 {@code status="ENABLED"}、{@code deleted=0}（{@code @TableLogic} 字段用 int 存储）。
   *
   * @param vo 配置 VO
   * @return 配置实体（含预生成 ID 和审计字段）
   */
  private Config toEntityWithIdForImport(ConfigVO vo) {
    Config entity = new Config();
    entity.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr());
    entity.setConfigGroup(vo.getConfigGroup());
    entity.setConfigKey(vo.getConfigKey());
    entity.setConfigValue(vo.getConfigValue());
    entity.setValueType(vo.getValueType());
    entity.setDefaultValue(vo.getDefaultValue());
    entity.setDescription(vo.getDescription());
    entity.setIsPublic(vo.getIsPublic());
    entity.setSortOrder(vo.getSortOrder());
    entity.setStatus(vo.getStatus() != null ? vo.getStatus() : "ENABLED");
    entity.setDeleted(0);
    entity.setRevision(0);
    entity.setCreatedAt(java.time.LocalDateTime.now());
    entity.setUpdatedAt(java.time.LocalDateTime.now());
    entity.setCreatedBy(getCurrentUserId());
    entity.setUpdatedBy(getCurrentUserId());
    entity.setTenantId(com.njydsz.common.tenant.TenantContextHolder.getTenantId());
    return entity;
  }

  /**
   * 获取当前用户 ID（私有）。
   *
   * <p>从 RequestContext 获取当前操作人 ID，未登录时返回 "system"。
   *
   * @return 当前用户 ID
   */
  private String getCurrentUserId() {
    try {
      return com.njydsz.common.core.context.RequestContext.getUserId();
    } catch (Exception e) {
      return "system";
    }
  }

  /**
   * Config 实体转换为 Excel VO
   *
   * @param entity 配置实体
   * @return Excel VO
   */
  private ConfigExcelVO toExcelVO(Config entity) {
    ConfigExcelVO vo = new ConfigExcelVO();
    vo.setConfigGroup(entity.getConfigGroup());
    vo.setConfigKey(entity.getConfigKey());
    vo.setConfigValue(entity.getConfigValue());
    vo.setValueType(entity.getValueType());
    vo.setDefaultValue(entity.getDefaultValue());
    vo.setDescription(entity.getDescription());
    vo.setIsPublic(entity.getIsPublic());
    vo.setSortOrder(entity.getSortOrder());
    vo.setStatus(entity.getStatus());
    return vo;
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
