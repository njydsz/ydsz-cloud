package com.njydsz.system.server.service.impl;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.dto.EntityVersionDTO;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.event.VersionSnapshotEvent;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.CursorPageResponse;
import com.njydsz.system.domain.vo.ImportResult;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigExcelService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.rollback.ConfigRollbackStrategy;
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
 * @see com.njydsz.system.infra.entity.ConfigDO 系统配置实体
 * @see com.njydsz.system.domain.enums.ConfigValueType 值类型枚举
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

  /** 游标分页页大小上限 */
  private static final int MAX_CURSOR_PAGE_SIZE = 500;

  // ============================== 依赖注入 ==============================

  /** 系统配置仓储 */
  private final ConfigRepository configRepository;

  /** 系统监控指标采集器 */
  private final SystemMetrics metrics;

  /** 系统配置属性 */
  private final SystemProperties properties;

  /** 统一领域事件发布门面（ObjectProvider 可选注入，common-event 未引入时安全降级，见《云顶编码规范》27.4） */
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** 统一实体版本服务（写操作时创建版本快照） */
  private final EntityVersionService entityVersionService;

  /** Spring Cache 管理器（用于按 key 精准失效缓存，替代 allEntries 全量清空） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器（SpEL 与手动 evict 共用） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** Excel 导入导出服务（P1-1 拆分：环境迁移能力独立成类，本类专注 CRUD/缓存/事件编排） */
  private final ConfigExcelService configExcelService;

  /** 配置回滚策略（从快照 JSON 反序列化并重建配置资源） */
  private final ConfigRollbackStrategy rollbackStrategy;

  /** Spring 事件发布器（用于异步创建版本快照，P3-2 版本快照异步化） */
  private final ApplicationEventPublisher eventPublisher;

  // ============================== CRUD ==============================

  @Override
  public PageResponse<List<ConfigVO>> page(ConfigPageQuery query) {
    return configRepository.findByPage(query);
  }

  @Override
  public CursorPageResponse<ConfigVO> pageByCursor(
      String configGroup, String configKey, int pageSize, String cursor) {
    // 1. 校验并归一化页大小
    int safePageSize = Math.min(Math.max(pageSize, 1), MAX_CURSOR_PAGE_SIZE);

    // 2. 查询数据（seek method：ID > cursor，仓储内部封装 QueryWrapper）
    List<ConfigVO> records =
        configRepository.findForCursor(configGroup, configKey, cursor, safePageSize);

    // 3. 计算下一页游标（本页满且存在后续数据时）
    String nextCursor = null;
    if (records.size() == safePageSize) {
      String lastId = records.get(records.size() - 1).getId();
      if (configRepository.existsAfterCursor(configGroup, configKey, lastId)) {
        nextCursor = lastId;
      }
    }

    return CursorPageResponse.of(records, nextCursor);
  }

  @Override
  public ConfigVO getById(String id) {
    return configRepository.findById(id).orElse(null);
  }

  @Override
  @Caching(
      evict = {
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configValue(#dto.configKey)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configGroup(#dto.configGroup)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configPublic()")
      })
  @Transactional(rollbackFor = Exception.class)
  public String save(ConfigDTO dto) {
    checkDuplicateKey(dto);
    validateConfigValueFormat(dto);
    // 版本快照：新建配置无需快照（变更前不存在）
    configRepository.insert(dto);
    publishConfigChangedEvent(dto.getConfigKey(), dto.getConfigGroup());
    return dto.getId();
  }

  @Override
  @Caching(
      evict = {
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configValue(#dto.configKey)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configGroup(#dto.configGroup)"),
        @CacheEvict(
            value = CacheConstants.SYSTEM_CONFIG_CACHE,
            key = "@cacheKeyBuilder.configPublic()")
      })
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(ConfigDTO dto) {
    validateConfigValueFormat(dto);
    // 版本快照：查询变更前状态
    ConfigVO before = configRepository.findByKeyIgnoreStatus(dto.getConfigKey()).orElse(null);
    String snapshotJson = before != null ? YdszJson.toJson(before) : null;
    boolean updated = configRepository.updateById(dto);
    if (updated) {
      // 分组变更时旧分组缓存一并失效
      if (before != null && !Objects.equals(before.getConfigGroup(), dto.getConfigGroup())) {
        evictConfigGroup(before.getConfigGroup());
      }
      // 创建版本快照（P3-2 异步化：事务提交后由监听器创建）
      eventPublisher.publishEvent(
          new VersionSnapshotEvent(
              this,
              EntityVersionDTO.builder()
                  .resourceType(EntityVersionService.RESOURCE_TYPE_CONFIG)
                  .resourceKey(dto.getConfigKey())
                  .resourceGroup(dto.getConfigGroup())
                  .version(SystemVersionUtils.nextVersion())
                  .changeLog("更新配置: " + dto.getConfigKey())
                  .snapshotJson(snapshotJson)
                  .build()));
      publishConfigChangedEvent(dto.getConfigKey(), dto.getConfigGroup());
    }
    return updated;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    ConfigVO entity = configRepository.findById(id).orElse(null);
    // 版本快照：查询变更前状态
    String snapshotJson = entity != null ? YdszJson.toJson(entity) : null;
    boolean removed = configRepository.deleteById(id);
    if (removed && entity != null) {
      // 精准失效单 key / 分组 / 公开配置缓存
      evictConfigCaches(entity.getConfigKey(), entity.getConfigGroup());
      // 创建版本快照（P3-2 异步化：事务提交后由监听器创建）
      eventPublisher.publishEvent(
          new VersionSnapshotEvent(
              this,
              EntityVersionDTO.builder()
                  .resourceType(EntityVersionService.RESOURCE_TYPE_CONFIG)
                  .resourceKey(entity.getConfigKey())
                  .resourceGroup(entity.getConfigGroup())
                  .version(SystemVersionUtils.nextVersion())
                  .changeLog("删除配置: " + entity.getConfigKey())
                  .snapshotJson(snapshotJson)
                  .build()));
      publishConfigChangedEvent(entity.getConfigKey(), entity.getConfigGroup());
    }
    return removed;
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
    ConfigVO config = configRepository.findEnabledByKey(configKey).orElse(null);
    return config != null ? config.getConfigValue() : null;
  }

  @Override
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configGroup(#p0)")
  public List<ConfigVO> getConfigsByGroup(String configGroup) {
    return configRepository.findEnabledByGroup(configGroup);
  }

  @Override
  @Cacheable(value = CacheConstants.SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configPublic()")
  public List<ConfigVO> listPublicConfigs() {
    return configRepository.findPublicEnabled();
  }

  // ============================== 私有方法 ==============================

  /**
   * 校验同一分组下配置键是否重复。
   *
   * <p>写入前按 {@code (configGroup, configKey)} 唯一性预检， 命中已有记录时抛出 {@link
   * IllegalArgumentException}，避免唯一索引冲突导致写入失败。
   *
   * @param dto 待校验的配置 DTO
   */
  private void checkDuplicateKey(ConfigDTO dto) {
    if (configRepository.existsByGroupAndKey(dto.getConfigGroup(), dto.getConfigKey())) {
      throw BusinessException.of(SystemExceptionCode.CONFIG_KEY_DUPLICATE)
          .data("configGroup", dto.getConfigGroup())
          .data("configKey", dto.getConfigKey());
    }
  }

  /**
   * 校验配置值格式与声明值类型是否匹配（P1-10 接线：strictValidation 开关 + 告警指标）。
   *
   * <p>委托 {@link ConfigValueType#validateFormat} 完成格式权威校验（值类型规则的收敛实现）。
   * <ul>
   *   <li>{@code strictValidation=true}（推荐生产）：格式非法直接抛 {@link BusinessException} 阻止脏数据落库
   *   <li>{@code strictValidation=false}（默认，向后兼容存量非法值）：仅告警放行 + 记录 {@code config_validation_warning_total} 指标
   * </ul>
   *
   * @param dto 待校验的配置 DTO
   */
  private void validateConfigValueFormat(ConfigDTO dto) {
    String formatError = ConfigValueType.validateFormat(dto.getValueType(), dto.getConfigValue());
    if (formatError == null) {
      return;
    }
    if (properties.getConfig().isStrictValidation()) {
      throw BusinessException.of(SystemExceptionCode.CONFIG_VALUE_FORMAT_INVALID)
          .data("configKey", dto.getConfigKey())
          .data("valueType", dto.getValueType())
          .data("reason", formatError);
    }
    log.warn(
        "[ConfigService] 配置值格式告警放行（strictValidation=false）: key={}, reason={}",
        dto.getConfigKey(),
        formatError);
    metrics.recordConfigValidationWarning();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(String resourceKey, String targetVersion, String operatorId) {
    return entityVersionService.rollbackTo(
        EntityVersionService.RESOURCE_TYPE_CONFIG,
        resourceKey,
        targetVersion,
        operatorId,
        rollbackStrategy);
  }

  // ============================== 导入导出 ==============================

  @Override
  public byte[] exportConfigs(String configGroup) {
    return configExcelService.exportConfigs(configGroup);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ImportResult importConfigs(InputStream inputStream) {
    return configExcelService.importConfigs(inputStream);
  }

  /**
   * 读取 Excel 配置数据（私有）。
   *
   * @param inputStream Excel 输入流
   * @return 配置 Excel 行列表
   */
  /**
   * 广播配置变更事件（用于跨实例本地缓存失效感知）。
   *
   * <p>通过可选的 {@code OutboxService} 追加 {@code CONFIG_CHANGED} 事件。开启
   * {@code ydsz.system.cache.cross-instance-enabled=true} 时，事件将触发 Redis Pub/Sub 失效总线通知其他实例清除本地缓存。
   * 事件发布失败仅告警，不影响主流程。
   *
   * @param configKey 配置键（为 null 时跳过）
   * @param configGroup 配置分组（为 null 时跳过）
   */
  private void publishConfigChangedEvent(String configKey, String configGroup) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("Config")
            .aggregateId(configKey)
            .eventType(DomainEventTypes.CONFIG_CHANGED)
            .metadata("configKey", configKey)
            .metadata("configGroup", configGroup)
            .build());
  }
}
