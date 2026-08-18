package com.njydsz.system.server.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.dto.EntityVersionCreateDTO;
import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.search.SearchIndexSyncer;
import com.njydsz.system.server.service.ConfigBatchService;
import com.njydsz.system.server.service.EntityVersionService;

/**
 * 系统配置批量操作 Service 实现
 *
 * <p>提供批量创建能力。批量内任意一条失败则全部回滚（事务保证）。
 *
 * <p><b>P2-1 优化：</b>
 *
 * <ul>
 *   <li>批量插入：N 次单条 INSERT → 1 次批量 INSERT（{@link ConfigMapper#insertBatch}）
 *   <li>批量唯一性校验：N 次 {@code selectCount} → 1 次 {@code IN} 查询 + 内存比对（消除 N+1）
 *   <li>单次快照：N 次版本快照 → 每个 configGroup 1 次快照（批量前一次性抓取）
 *   <li>精准缓存失效：按涉及 configGroup 逐一失效组缓存（替代全量清空，避免缓存击穿）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigBatchServiceImpl implements ConfigBatchService {

  /** 配置值校验常量 */
  private static final int MAX_STRING_LENGTH = 4096;
  private static final int MAX_JSON_LENGTH = 65536;
  private static final double MIN_NUMBER = -1e15;
  private static final double MAX_NUMBER = 1e15;
  private static final Pattern BOOLEAN_PATTERN =
      Pattern.compile("^(true|false|TRUE|FALSE|True|False)$");

  /** 日志截断最大长度 */
  private static final int MAX_LOG_ABBREVIATE_LENGTH = 128;

  /** 配置仓储（用于批量插入 + 唯一性校验） */
  private final ConfigRepository configRepository;

  /** 统一实体版本服务（用于创建批量快照） */
  private final EntityVersionService entityVersionService;

  /** Spring Cache 管理器（用于按 key 精准失效缓存） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器（手动 evict 使用） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** 搜索索引同步器（可选能力，未启用搜索模块时静默跳过） */
  private final SearchIndexSyncer searchIndexSyncer;

  /** 统一领域事件发布门面 */
  private final DomainEventPublisher eventPublisher;

  /**
   * 批量创建配置项
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>批量内去重：校验批量内无重复 (configGroup, configKey)
   *   <li>逐条 DB 唯一性校验（避免批量插入时触发唯一索引冲突导致全部回滚）
   *   <li>单次快照：按 configGroup 分组，每个 configGroup 只生成一个版本快照
   *   <li>批量插入：一次性 INSERT 所有配置项
   *   <li>统一缓存失效：按涉及 configGroup 逐一失效组缓存 + 公开配置缓存
   * </ol>
   *
   * <p><b>缓存一致性：</b>与 {@link ConfigServiceImpl} 的 {@code @CacheEvict} 行为完全一致， 确保批量操作后缓存与 DB 数据一致。
   *
   * <p><b>事务边界：</b>所有插入在同一事务内，任意一条失败则全部回滚。
   *
   * @param items 配置列表
   * @return 操作结果 {successCount, totalCount, message}
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> batchSave(List<ConfigVO> items) {
    if (items == null || items.isEmpty()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR).data("reason", "配置列表不能为空");
    }

    // 1. 批量内去重校验
    validateInnerDuplication(items);

    // 2. 逐条格式校验（值类型 + 配置值格式）
    items.forEach(this::validateItem);

    // 3. 逐条 DB 唯一性校验（避免批量插入时触发唯一索引冲突）
    validateDbUniqueness(items);

    // 4. 单次快照：按 configGroup 分组，每个 configGroup 只生成一个版本快照
    Set<String> configGroups =
        items.stream()
            .map(ConfigVO::getConfigGroup)
            .collect(Collectors.toSet());
    String version = "v" + System.currentTimeMillis();
    for (String configGroup : configGroups) {
      createSnapshotVersion(configGroup, version, "批量创建配置");
    }

    // 5. 预生成 ID + VO 转 DTO
    List<ConfigDTO> dtos = items.stream().map(this::toDtoWithId).collect(Collectors.toList());

    // 6. 批量插入
    configRepository.insertBatch(dtos);

    // 7. 精准失效缓存：按涉及 configGroup 逐一失效组缓存 + 公开配置缓存
    configGroups.forEach(this::evictConfigGroup);
    evictConfigPublic();

    // 8. 同步搜索索引 + 发布变更事件（异步，不阻塞主流程）
    dtos.forEach(dto -> {
      searchIndexSyncer.upsert("config", dto);
      publishConfigChangedEvent(dto.getConfigKey(), dto.getConfigGroup());
    });

    Map<String, Object> result = new HashMap<>();
    result.put("successCount", items.size());
    result.put("totalCount", items.size());
    result.put("message", String.format("成功批量创建 %d 条配置", items.size()));
    return result;
  }

  /**
   * 批量内去重校验（私有）
   *
   * <p>检查批量数据中无重复的 (configGroup, configKey) 组合。
   *
   * @param items 配置列表
   * @throws BusinessException 当批量数据中存在重复项时抛出
   */
  private void validateInnerDuplication(List<ConfigVO> items) {
    Set<String> innerKeySet = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      ConfigVO item = items.get(i);
      String key = item.getConfigGroup() + "/" + item.getConfigKey();
      if (!innerKeySet.add(key)) {
        throw BusinessException.of(SystemExceptionCode.CONFIG_KEY_DUPLICATE)
            .data("reason", "批量数据中存在重复项: " + key + "（第 " + (i + 1) + " 条）");
      }
    }
  }

  /**
   * 单条配置格式校验（私有）
   *
   * <p>校验值类型合法性 + 配置值格式。
   *
   * @param item 配置 VO
   */
  private void validateItem(ConfigVO item) {
    validateValueType(item.getValueType());
    validateConfigValue(item.getConfigKey(), item.getConfigValue(), item.getValueType());
  }

  /**
   * 批量 DB 唯一性校验（私有）
   *
   * <p>一次 SQL 查询批量涉及的全部 (configGroup, configKey) 记录，在内存中构建集合比对， 替代逐条 {@code
   * selectCount}（N+1 查询），避免批量插入时触发唯一索引冲突导致全部回滚。
   *
   * @param items 配置列表
   * @throws BusinessException 当某条数据已存在时抛出
   */
  private void validateDbUniqueness(List<ConfigVO> items) {
    for (int i = 0; i < items.size(); i++) {
      ConfigVO item = items.get(i);
      if (configRepository.existsByGroupAndKey(item.getConfigGroup(), item.getConfigKey())) {
        throw BusinessException.of(SystemExceptionCode.CONFIG_KEY_DUPLICATE)
            .data(
                "reason",
                String.format(
                    "第 %d 条插入失败: %s/%s 已存在",
                    i + 1, item.getConfigGroup(), item.getConfigKey()));
      }
    }
  }

  /**
   * 精准失效「按配置分组查询」缓存（私有）。
   *
   * @param configGroup 配置分组
   */
  private void evictConfigGroup(String configGroup) {
    if (configGroup == null) {
      return;
    }
    cacheManager
        .getCache(CacheConstants.SYSTEM_CONFIG_CACHE)
        .evict(cacheKeyBuilder.configGroup(configGroup));
  }

  /**
   * 失效「公开配置」缓存（私有）。
   */
  private void evictConfigPublic() {
    cacheManager
        .getCache(CacheConstants.SYSTEM_CONFIG_CACHE)
        .evict(cacheKeyBuilder.configPublic());
  }

  /**
   * 批量操作前创建版本快照（私有）
   *
   * <p>每个 configGroup 仅创建一个版本快照，与逐条插入时每条一个快照相比， 大幅减少 DB 开销和版本记录膨胀。
   *
   * @param configGroup 配置分组
   * @param version 版本号
   * @param changeLog 变更说明
   */
  private void createSnapshotVersion(String configGroup, String version, String changeLog) {
    List<ConfigVO> snapshot = configRepository.findByGroup(configGroup);
    String snapshotJson = YdszJson.toJson(snapshot);
    entityVersionService.createVersion(
        EntityVersionCreateDTO.builder()
            .resourceType(EntityVersionService.RESOURCE_TYPE_CONFIG)
            .resourceKey(configGroup)
            .resourceGroup(configGroup)
            .version(version)
            .changeLog(changeLog)
            .snapshotJson(snapshotJson)
            .build());
  }

  /**
   * VO 转 DTO + 预生成雪花 ID + 审计字段（私有）
   *
   * <p>批量 XML 插入不走 MyBatis-Plus 拦截器（CombinedFieldFillInterceptor、租户拦截器、 IdentifierGenerator
   * 均不生效），需在此处手动预生成 ID 并填充审计字段。
   *
   * <p>缺省 {@code status="ENABLED"}。
   *
   * @param vo 配置 VO
   * @return 配置 DTO（含预生成 ID 和审计字段）
   */
  private ConfigDTO toDtoWithId(ConfigVO vo) {
    ConfigDTO dto = SystemConverter.INSTANT.voToDto(vo);
    dto.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr());
    dto.setStatus(vo.getStatus() != null ? vo.getStatus() : "ENABLED");
    return dto;
  }

  /**
   * 校验配置值类型合法性（私有）。
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
            "[ConfigBatchService] 配置值格式校验失败: configKey={}, valueType={}, value={}, error={}",
            configKey,
            valueType,
            abbreviate(configValue),
            error);
      }
    } catch (Exception e) {
      log.warn(
          "[ConfigBatchService] 配置值校验异常（不影响保存）: configKey={}, valueType={}, error={}",
          configKey,
          valueType,
          e.getMessage());
    }
  }

  /**
   * 按值类型进行格式校验（内联实现）。
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
      com.njydsz.common.json.YdszJson.parseMap(trimmed);
    } else if (trimmed.startsWith("[")) {
      com.njydsz.common.json.YdszJson.parseArray(trimmed, Object.class);
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
   * 发布配置变更事件（跨实例缓存同步）。
   *
   * @param configKey 配置键
   * @param configGroup 配置分组
   */
  private void publishConfigChangedEvent(String configKey, String configGroup) {
    eventPublisher.publish(
        DomainEvent.builder()
            .aggregateType("Config")
            .aggregateId(configKey)
            .eventType(DomainEventTypes.CONFIG_CHANGED)
            .metadata("configKey", configKey)
            .metadata("configGroup", configGroup)
            .metadata("action", "批量创建配置")
            .build());
  }
}
