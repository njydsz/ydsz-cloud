package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 系统配置 Service 实现
 *
 * <p>对 {@link ConfigService} 接口的完整实现，是「系统配置中心」的核心业务逻辑层。
 * 集成 Redis 缓存、Micrometer 指标、缓存穿透防护和配置变更事件总线。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@code page} / {@code getById} / {@code save} / {@code updateById} / {@code removeById}，
 *       全部走 {@code @Transactional} 事务保证</li>
 *   <li><b>缓存读</b>：{@code getConfigValue}（单 key） / {@code getConfigsByGroup}（组批量） —
 *       走 Redis 二级缓存 + 本地 Caffeine 一级缓存</li>
 *   <li><b>公开配置</b>：{@code listPublicConfigs} — 前端「公开配置」接口数据源</li>
 *   <li><b>缓存穿透防护</b>：DB 不存在的 key 缓存「{@code __NULL__}」哨兵值 1min</li>
 *   <li><b>变更广播</b>：通过 {@code OutboxService} 发布 {@code ConfigChangeEvent}，
 *       订阅者可监听 {@code ydsz.workflow.sla-default-hours} 等关键配置变更</li>
 *   <li><b>搜索同步</b>：通过 {@link SearchIndexEventBridge} 同步配置变更到 ES 索引</li>
 *   <li><b>指标埋点</b>：通过 {@link com.njydsz.system.server.metrics.SystemMetrics} 暴露 Prometheus 指标</li>
 * </ul>
 *
 * <p><b>缓存设计：</b>
 * <ul>
 *   <li>单 key 缓存：{@code system:config:value:{configKey}}，TTL 取自配置（默认 5min）</li>
 *   <li>组批量缓存：{@code system:config:group:{configGroup}}，TTL 5min</li>
 *   <li>公开配置缓存：{@code system:config:public}，TTL 5min</li>
 *   <li>空值哨兵：{@code __NULL__}，TTL 1min（防恶意刷不存在 key）</li>
 *   <li>写操作触发 {@code @CacheEvict} 主动失效</li>
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}；
 * 读方法不开启事务，依赖 MyBatis 自动提交。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ConfigService 配置 Service 接口
 * @see com.njydsz.system.domain.entity.Config 系统配置实体
 * @see com.njydsz.system.domain.enums.ConfigValueType 值类型枚举
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    /** 单个配置值缓存键前缀：system:config:value:{configKey} */
    private static final String CACHE_KEY_PREFIX = "system:config:value:";
    /** 配置组缓存键前缀：system:config:group:{configGroup} */
    private static final String CACHE_GROUP_PREFIX = "system:config:group:";
    /** 公开配置列表缓存键：system:config:public */
    private static final String CACHE_PUBLIC_KEY = "system:config:public";
    /** 空值哨兵，用于防缓存穿透 */
    private static final String NULL_SENTINEL = "__NULL__";
    /** 空值哨兵 TTL（1 分钟） */
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(1);

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
    /** Redis String 操作组件 */
    private final RedisStringOps stringOps;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;
    /** 系统配置属性 */
    private final SystemProperties properties;
    /** Outbox 服务（可选依赖，用于发布配置变更事件） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;
    private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

    // ============================== CRUD ==============================

    @Override
    public PageResponse<List<ConfigVO>> page(ConfigPageQuery query) {
        QueryWrapper<Config> wrapper = buildQueryWrapper(query);
        Page<Config> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
        IPage<Config> result = configRepository.getConfigMapper().selectPage(mpPage, wrapper);
        List<ConfigVO> vos = result.getRecords().stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PageResponse.success(result.getTotal(), result.getCurrent(), result.getSize(), vos);
    }

    @Override
    public ConfigVO getById(String id) {
        Config entity = configRepository.getConfigMapper().selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(ConfigDTO dto) {
        Config entity = toEntity(dto);
        validateValueType(entity.getValueType());
        checkDuplicateKey(entity);
        validateConfigValue(entity.getConfigKey(), entity.getConfigValue(), entity.getValueType());
        configRepository.getConfigMapper().insert(entity);
        evictCache(entity.getConfigKey(), entity.getConfigGroup());
        indexUpsert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ConfigDTO dto) {
        Config entity = toEntity(dto);
        validateValueType(entity.getValueType());
        validateConfigValue(entity.getConfigKey(), entity.getConfigValue(), entity.getValueType());
        boolean updated = configRepository.getConfigMapper().updateById(entity) > 0;
        if (updated) {
            evictCache(entity.getConfigKey(), entity.getConfigGroup());
            indexUpsert(entity);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Config entity = configRepository.getConfigMapper().selectById(id);
        boolean removed = configRepository.getConfigMapper().deleteById(id) > 0;
        if (removed && entity != null) {
            evictCache(entity.getConfigKey(), entity.getConfigGroup());
            indexDelete(id);
        }
        return removed;
    }

    /**
     * 同步配置变更到 ES 搜索索引（可选能力）。
     *
     * <p>通过 {@code ObjectProvider} 获取可选依赖 {@link SearchIndexEventBridge}，
     * 仅当搜索模块存在时才执行索引 upsert，避免对未启用搜索的环境产生硬依赖。
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
    public String getConfigValue(String configKey) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_KEY_PREFIX + configKey;
            String cached = stringOps.get(cacheKey, String.class);
            if (cached != null) {
                if (NULL_SENTINEL.equals(cached)) {
                    metrics.recordConfigCacheHit();
                    return null;
                }
                metrics.recordConfigCacheHit();
                return cached;
            }
            metrics.recordConfigCacheMiss();
            Config config = configRepository.getConfigMapper().selectByConfigKey(configKey);
            if (config != null) {
                stringOps.set(cacheKey, config.getConfigValue(), getCacheTtl());
                return config.getConfigValue();
            }
            stringOps.set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
            return null;
        } finally {
            metrics.recordConfigRead(System.nanoTime() - start);
        }
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<ConfigVO> getConfigsByGroup(String configGroup) {
        String cacheKey = CACHE_GROUP_PREFIX + configGroup;
        String cached = stringOps.get(cacheKey, String.class);
        if (cached != null) {
            metrics.recordConfigCacheHit();
            return YdszJson.parseArray(cached, ConfigVO.class);
        }
        metrics.recordConfigCacheMiss();
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("config_group", configGroup).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = configRepository.getConfigMapper().selectList(wrapper).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        stringOps.set(cacheKey, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<ConfigVO> listPublicConfigs() {
        String cached = stringOps.get(CACHE_PUBLIC_KEY, String.class);
        if (cached != null) {
            metrics.recordConfigCacheHit();
            return YdszJson.parseArray(cached, ConfigVO.class);
        }
        metrics.recordConfigCacheMiss();
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("is_public", 1).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = configRepository.getConfigMapper().selectList(wrapper).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        stringOps.set(CACHE_PUBLIC_KEY, YdszJson.toJson(vos), getCacheTtl());
        return vos;
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
     * <p>委托 {@link ConfigValueType#validate} 完成，
     * 非法类型将抛出 {@link IllegalArgumentException} 阻止脏数据落库。
     *
     * @param valueType 值类型字符串
     */
    private void validateValueType(String valueType) {
        ConfigValueType.validate(valueType);
    }

    /**
     * 对配置值进行格式校验（告警模式，不阻止保存）。
     *
     * <p><b>向后兼容：</b>校验失败仅记录告警日志，不阻止配置保存，
     * 保证现有非法配置值仍可继续使用，同时提示管理员修正。
     *
     * @param configKey   配置键（用于日志定位，为 null 时跳过）
     * @param configValue 配置值字符串（为 null 时跳过校验）
     * @param valueType   值类型字符串（为 null 或无法识别时跳过校验）
     */
    private void validateConfigValue(String configKey, String configValue, String valueType) {
        if (configKey == null || configValue == null || valueType == null) {
            return;
        }
        try {
            String error = validateValueByFormat(configValue, valueType);
            if (error != null) {
                log.warn("[ConfigService] 配置值格式校验失败: configKey={}, valueType={}, value={}, error={}",
                        configKey, valueType, abbreviate(configValue), error);
                if (metrics != null) {
                    metrics.recordConfigValidationWarning();
                }
            }
        } catch (Exception e) {
            log.warn("[ConfigService] 配置值校验异常（不影响保存）: configKey={}, valueType={}, error={}",
                    configKey, valueType, e.getMessage());
        }
    }

    /**
     * 按值类型进行格式校验（内联实现，替代已移除的 Schema 校验引擎）。
     *
     * <p>校验规则：
     * <ul>
     *   <li>STRING — 长度 ≤ {@value #MAX_STRING_LENGTH}</li>
     *   <li>INTEGER / NUMBER — 可解析为数值且在 [{}, {}] 范围内</li>
     *   <li>BOOLEAN — 必须为 true/false</li>
     *   <li>JSON — 必须为合法 JSON 且长度 ≤ {@value #MAX_JSON_LENGTH}</li>
     * </ul>
     *
     * @return 错误描述，null 表示通过
     */
    private static String validateValueByFormat(String configValue, String valueType) {
        try {
            ConfigValueType type = ConfigValueType.valueOf(valueType.toUpperCase());
            return switch (type) {
                case STRING -> configValue.length() > MAX_STRING_LENGTH
                        ? "字符串长度超过限制 " + MAX_STRING_LENGTH : null;
                case NUMBER -> {
                    double v = Double.parseDouble(configValue.trim());
                    if (v < MIN_NUMBER || v > MAX_NUMBER) {
                        yield "数值超出范围 [" + MIN_NUMBER + ", " + MAX_NUMBER + "]";
                    }
                    yield null;
                }
                case BOOLEAN -> BOOLEAN_PATTERN.matcher(configValue.trim()).matches()
                        ? null : "布尔值必须是 true/false";
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
     * <p>写入前按 {@code (configGroup, configKey)} 唯一性预检，
     * 命中已有记录时抛出 {@link IllegalArgumentException}，避免唯一索引冲突导致写入失败。
     *
     * @param entity 待校验的配置实体
     */
    private void checkDuplicateKey(Config entity) {
        QueryWrapper<Config> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("config_group", entity.getConfigGroup())
                .eq("config_key", entity.getConfigKey());
        if (configRepository.getConfigMapper().selectCount(checkWrapper) > 0) {
            throw BusinessException.of(SystemExceptionCode.CONFIG_KEY_DUPLICATE)
                    .data("configGroup", entity.getConfigGroup())
                    .data("configKey", entity.getConfigKey());
        }
    }

    /**
     * 失效配置相关缓存并广播变更事件。
     *
     * <p>依次删除单 key 缓存、分组缓存、公开配置缓存；
     * 并通过可选的 {@code OutboxService} 追加 {@code CONFIG_CHANGED} 事件，
     * 订阅方可感知配置变更（如热更新限流阈值）。事件发布失败仅告警，不影响主流程。
     *
     * @param configKey 配置键（为 null 时跳过单 key 缓存失效）
     * @param configGroup 配置分组（为 null 时跳过分组缓存失效）
     */
    private void evictCache(String configKey, String configGroup) {
        if (configKey != null) {
            stringOps.del(CACHE_KEY_PREFIX + configKey);
        }
        if (configGroup != null) {
            stringOps.del(CACHE_GROUP_PREFIX + configGroup);
        }
        stringOps.del(CACHE_PUBLIC_KEY);

        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService != null) {
            try {
                Map<String, String> payload = new HashMap<>();
                if (configKey != null) {
                    payload.put("configKey", configKey);
                }
                if (configGroup != null) {
                    payload.put("configGroup", configGroup);
                }
                outboxService.appendToOutbox(
                        "Config", null, StandardEventTypes.CONFIG_CHANGED,
                        YdszJson.toJson(payload));
            } catch (Exception e) {
                log.warn("Failed to publish CONFIG_CHANGED event: error={}", e.getMessage());
            }
        }
    }

    /**
     * 解析配置缓存 TTL。
     *
     * <p>取 {@code SystemProperties} 配置值；当配置非法（{@code <=0}）时回退到 5 分钟默认值，
     * 防止错误配置导致缓存被立即淘汰而失去作用。
     *
     * @return 缓存过期时长
     */
    private Duration getCacheTtl() {
        int minutes = properties.getConfig().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }
}
