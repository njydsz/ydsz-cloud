package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.entity.Variable;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.infra.mapper.VariableMapper;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.VariableService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 系统变量 Service 实现
 *
 * <p>实现 {@link VariableService} 接口，封装系统变量的 CRUD、按 key 查询值、分页查询等能力。
 * 集成 Redis 缓存（TTL 可配置）、Micrometer 指标、缓存穿透防护，与 {@link ConfigServiceImpl} 能力对齐。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>封装变量表增删改查（事务保证）</li>
 *   <li>按变量键快速查询（Redis 缓存）</li>
 *   <li>缓存穿透防护（空值哨兵）</li>
 *   <li>行级数据权限过滤（{@code @DataScope}）</li>
 * </ul>
 *
 * <p><b>缓存设计：</b>
 * <ul>
 *   <li>缓存键：{@code system:variable:value:{variableKey}}</li>
 *   <li>TTL 取自配置 {@code ydsz.system.variable.cache-ttl-minutes}，默认 5 分钟</li>
 *   <li>空值哨兵（{@link #NULL_SENTINEL}）防缓存穿透，1 分钟 TTL</li>
 *   <li>写操作触发缓存失效（{@link #evictCache}）</li>
 * </ul>
 *
 * <p><b>与 ConfigService 的区别：</b>Variable 用于业务侧动态参数（如当前生效的会计年度、最近结算月份等），
 * 业务方可通过 {@code VariableClient}（Feign）远程查询；Config 用于系统级配置，由后端模块消费。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableServiceImpl implements VariableService {

    /** 变量值缓存键前缀：system:variable:value:{variableKey} */
    private static final String CACHE_KEY_PREFIX = "system:variable:value:";
    /** 空值哨兵，用于防缓存穿透 */
    private static final String NULL_SENTINEL = "__NULL__";
    /** 空值哨兵 TTL（1 分钟） */
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(1);

    /** 系统变量 Mapper */
    private final VariableMapper mapper;
    /** Redis 缓存服务 */
    private final RedisService redisService;
    /** 系统配置属性 */
    private final SystemProperties properties;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;

    /**
     * {@inheritDoc}
     */
    @Override
    public VariableVO getById(String id) {
        Variable entity = mapper.selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>优先查 Redis 缓存（含空值哨兵防穿透），未命中时查 DB 并回写缓存。
     * 仅返回 status=ENABLED 的变量。
     *
     * @param variableKey 变量键
     * @return 变量值字符串，不存在时返回 null
     */
    @Override
    public String getVariableValue(String variableKey) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_KEY_PREFIX + variableKey;
            String cached = redisService.get(cacheKey, String.class);
            if (cached != null) {
                if (NULL_SENTINEL.equals(cached)) {
                    metrics.recordVariableCacheHit();
                    return null;
                }
                metrics.recordVariableCacheHit();
                return cached;
            }
            metrics.recordVariableCacheMiss();
            QueryWrapper<Variable> wrapper = new QueryWrapper<>();
            wrapper.eq("variable_key", variableKey).eq("status", "ENABLED");
            Variable entity = mapper.selectOne(wrapper);
            if (entity != null) {
                redisService.set(cacheKey, entity.getVariableValue(), getCacheTtl());
                return entity.getVariableValue();
            }
            redisService.set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
            return null;
        } finally {
            metrics.recordVariableRead(System.nanoTime() - start);
        }
    }

    /**
     * {@inheritDoc}
     * <p>支持按 variableKey 模糊匹配、status 精确匹配过滤。
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public IPage<VariableVO> page(int pageNum, int pageSize, String variableKey, String status) {
        QueryWrapper<Variable> wrapper = new QueryWrapper<>();
        if (variableKey != null && !variableKey.isBlank()) {
            wrapper.like("variable_key", variableKey);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        IPage<Variable> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<VariableVO> vos = page.getRecords().stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
        Page<VariableVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部变量列表（不区分状态）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<VariableVO> list() {
        return mapper.selectList(null).stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>插入后清除变量键对应的缓存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(VariableDTO dto) {
        Variable entity = toEntity(dto);
        mapper.insert(entity);
        evictCache(entity.getVariableKey());
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>更新成功后清除变量键对应的缓存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(VariableDTO dto) {
        Variable entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result && entity.getVariableKey() != null) {
            evictCache(entity.getVariableKey());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>删除成功后清除变量键对应的缓存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Variable entity = mapper.selectById(id);
        boolean result = mapper.deleteById(id) > 0;
        if (result && entity != null && entity.getVariableKey() != null) {
            evictCache(entity.getVariableKey());
        }
        return result;
    }

    /**
     * 清除指定变量键的缓存。
     *
     * @param variableKey 变量键
     */
    private void evictCache(String variableKey) {
        if (variableKey != null) {
            redisService.delete(CACHE_KEY_PREFIX + variableKey);
        }
    }

    /**
     * 获取变量缓存 TTL，从 {@link SystemProperties.Variable#getCacheTtlMinutes()} 读取，
     * 默认 5 分钟。
     *
     * @return 缓存 TTL
     */
    private Duration getCacheTtl() {
        int minutes = properties.getVariable().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }

    /**
     * DTO 转换为 DO。
     *
     * <p>缺省 status = {@code "ENABLED"}，保证新建的变量默认可用。
     *
     * @param dto 数据传输对象
     * @return 数据库实体
     */
    private Variable toEntity(VariableDTO dto) {
        Variable entity = new Variable();
        entity.setId(dto.getId());
        entity.setVariableKey(dto.getVariableKey());
        entity.setVariableValue(dto.getVariableValue());
        entity.setValueType(dto.getValueType());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
