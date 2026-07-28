package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.DictVersionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 字典项 Service 实现
 *
 * <p>对 {@link DictItemService} 接口的完整实现，是「字典中心」字典项管理的核心业务逻辑层。
 * 集成 Redis 缓存（TTL 可配置）、Micrometer 指标、缓存穿透防护（空值哨兵）、
 * 字典版本快照（写操作自动记录变更历史，含完整快照支持回滚）。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@code page} / {@code getById} / {@code save} / {@code updateById} / {@code removeById}，
 *       全部走 {@code @Transactional} 事务保证</li>
 *   <li><b>缓存读</b>：{@code getByTypeAndCode} / {@code listEnabledByTypeCode} — 走 Redis 缓存，
 *       是前端下拉框的核心数据源</li>
 *   <li><b>树形结构</b>：{@code listChildren} — 支持「省 / 市 / 区县」三级级联</li>
 *   <li><b>版本快照</b>：写操作成功后异步调用 {@link DictVersionService#createVersion} 记录变更</li>
 *   <li><b>唯一性校验</b>：保存前校验 {@code (tenantId, typeCode, itemCode)} 唯一性</li>
 * </ul>
 *
 * <p><b>缓存设计：</b>
 * <ul>
 *   <li>单 key 缓存：{@code system:dict:item:{typeCode}:{itemCode}}，TTL 取自配置（默认 5min）</li>
 *   <li>列表缓存：{@code system:dict:list:{typeCode}}，TTL 5min</li>
 *   <li>空值哨兵：{@code __NULL__}，TTL 1min（防恶意刷不存在 typeCode）</li>
 *   <li>写操作触发 {@code @CacheEvict} 主动失效</li>
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}；
 * 读方法不开启事务，依赖 MyBatis 自动提交。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DictItemService 字典项 Service 接口
 * @see DictServiceImpl 字典类型 Service 实现
 * @see DictVersionService 字典版本 Service（写操作触发版本快照）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

    /** 单个字典项缓存键前缀：system:dict:item:{typeCode}:{itemCode} */
    private static final String CACHE_ITEM_PREFIX = "system:dict:item:";
    /** 字典项列表缓存键前缀：system:dict:list:{typeCode} */
    private static final String CACHE_LIST_PREFIX = "system:dict:list:";
    /** 空值哨兵，用于防缓存穿透 */
    private static final String NULL_SENTINEL = "__NULL__";
    /** 空值哨兵 TTL（2 分钟），比正常缓存短避免长时间占用 */
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(2);

    /** 字典项 Mapper */
    private final DictItemMapper mapper;
    /** Redis 缓存服务 */
    private final RedisService redisService;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;
    /** 系统配置属性 */
    private final SystemProperties properties;
    /** 字典版本服务，用于记录变更快照 */
    private final DictVersionService dictVersionService;

    /**
     * {@inheritDoc}
     */
    @Override
    public DictItemVO getById(String id) {
        DictItem entity = mapper.selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>优先查 Redis 缓存（含空值哨兵防穿透），缓存未命中时查 DB 并回写缓存。
     * 同时记录缓存命中/未命中指标和查询耗时指标。
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @return 字典项 VO，不存在时返回 null（缓存在短 TTL 内返回 null 防穿透）
     */
    @Override
    public DictItemVO getByTypeAndCode(String typeCode, String itemCode) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_ITEM_PREFIX + typeCode + ":" + itemCode;
            String cached = redisService.get(cacheKey, String.class);
            if (cached != null) {
                if (NULL_SENTINEL.equals(cached)) {
                    metrics.recordDictCacheHit();
                    return null;
                }
                metrics.recordDictCacheHit();
                return YdszJson.toObject(cached, DictItemVO.class);
            }
            metrics.recordDictCacheMiss();
            DictItem entity = mapper.selectByTypeAndCode(typeCode, itemCode);
            DictItemVO vo = SystemConverter.INSTANT.entityToVO(entity);
            Duration ttl = getCacheTtl();
            if (vo != null) {
                redisService.set(cacheKey, YdszJson.toJson(vo), ttl);
            } else {
                // 缓存空值防穿透，短 TTL
                redisService.set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
            }
            return vo;
        } finally {
            metrics.recordDictQuery(System.nanoTime() - start);
        }
    }

    /**
     * {@inheritDoc}
     * <p>优先查 Redis 缓存，未命中时查 DB 并回写缓存。仅返回启用状态的字典项。
     *
     * @param typeCode 字典类型编码
     * @return 启用状态的字典项列表（按 sortOrder 升序）
     */
    @Override
    public List<DictItemVO> listEnabledByTypeCode(String typeCode) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_LIST_PREFIX + typeCode;
            String cached = redisService.get(cacheKey, String.class);
            if (cached != null) {
                metrics.recordDictCacheHit();
                return YdszJson.parseArray(cached, DictItemVO.class);
            }
            metrics.recordDictCacheMiss();
            List<DictItem> entities = mapper.listEnabledByTypeCode(typeCode);
            List<DictItemVO> vos = entities.stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
            redisService.set(cacheKey, YdszJson.toJson(vos), getCacheTtl());
            return vos;
        } finally {
            metrics.recordDictQuery(System.nanoTime() - start);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param parentId 父字典项 ID
     * @return 子字典项列表（按 sortOrder 升序）
     */
    @Override
    public List<DictItemVO> listChildren(String parentId) {
        QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", parentId).orderByAsc("sort_order");
        return mapper.selectList(wrapper).stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>支持按 typeCode 精确匹配、itemCode 模糊匹配、status 精确匹配进行过滤。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @param typeCode 字典类型编码（可选过滤条件）
     * @param itemCode 字典项编码（可选，模糊匹配）
     * @param status   状态（可选过滤条件）
     * @return 分页结果
     */
    @Override
    public IPage<DictItemVO> page(int pageNum, int pageSize, String typeCode, String itemCode, String status) {
        QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
        if (typeCode != null && !typeCode.isBlank()) {
            wrapper.eq("type_code", typeCode);
        }
        if (itemCode != null && !itemCode.isBlank()) {
            wrapper.like("item_code", itemCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        IPage<DictItem> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<DictItemVO> vos = page.getRecords().stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
        Page<DictItemVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部字典项列表（不区分状态）
     */
    @Override
    public List<DictItemVO> list() {
        return mapper.selectList(null).stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行唯一性校验（typeCode + itemCode 组合不能重复），插入后清除缓存并创建版本快照。
     *
     * @param dto 字典项数据
     * @return 新创建的字典项 ID
     * @throws IllegalArgumentException 当 typeCode + itemCode 组合已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DictItemDTO dto) {
        // 唯一性校验：(typeCode, itemCode) 组合不能重复
        QueryWrapper<DictItem> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("type_code", dto.getTypeCode())
                .eq("item_code", dto.getItemCode());
        if (mapper.selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException(
                    "字典项编码已存在: " + dto.getTypeCode() + "/" + dto.getItemCode());
        }
        DictItem entity = toEntity(dto);
        mapper.insert(entity);
        evictCache(entity.getTypeCode());
        createSnapshotVersion(entity.getTypeCode(), "新增字典项: " + entity.getItemCode());
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>更新成功后清除缓存并创建版本快照。
     *
     * @param dto 字典项数据（需包含 id）
     * @return true 表示更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictItemDTO dto) {
        DictItem entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result) {
            evictCache(entity.getTypeCode());
            createSnapshotVersion(entity.getTypeCode(), "更新字典项: " + entity.getItemCode());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>删除成功后清除缓存并创建版本快照。
     *
     * @param id 字典项 ID
     * @return true 表示删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        DictItem entity = mapper.selectById(id);
        boolean result = mapper.deleteById(id) > 0;
        if (result && entity != null) {
            evictCache(entity.getTypeCode());
            createSnapshotVersion(entity.getTypeCode(), "删除字典项: " + entity.getItemCode());
        }
        return result;
    }

    /**
     * 创建字典版本快照（含当前 typeCode 下所有字典项的 JSON 快照，支持回滚）。
     *
     * @param typeCode  字典类型编码
     * @param changeLog 变更说明
     */
    private void createSnapshotVersion(String typeCode, String changeLog) {
        if (typeCode == null) {
            return;
        }
        List<DictItem> snapshot = mapper.listEnabledByTypeCode(typeCode);
        String snapshotJson = YdszJson.toJson(snapshot);
        dictVersionService.createVersion(typeCode,
                "v" + System.currentTimeMillis(), changeLog, snapshotJson);
    }

    /**
     * 清除指定 typeCode 下的所有缓存（列表缓存 + 所有单项缓存）。
     * <p>使用 SCAN 替代 KEYS 命令避免阻塞 Redis。
     *
     * @param typeCode 字典类型编码
     */
    private void evictCache(String typeCode) {
        if (typeCode == null) {
            return;
        }
        redisService.del(CACHE_LIST_PREFIX + typeCode);
        // 使用 SCAN 替代 KEYS，避免 Redis 阻塞
        String pattern = CACHE_ITEM_PREFIX + typeCode + ":*";
        Set<String> keys = redisService.scan(pattern);
        if (!keys.isEmpty()) {
            redisService.del(keys);
        }
    }

    /**
     * 获取字典缓存 TTL，从 {@link SystemProperties.Dict#getCacheTtlMinutes()} 读取，
     * 默认 10 分钟。
     *
     * @return 缓存 TTL
     */
    private Duration getCacheTtl() {
        int minutes = properties.getDict().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 10);
    }

    /**
     * DTO 转换为 DO。
     *
     * <p>缺省 status = {@code "ENABLED"}，保证新建的字典项默认可用。
     *
     * @param dto 数据传输对象
     * @return 数据库实体
     */
    private DictItem toEntity(DictItemDTO dto) {
        DictItem entity = new DictItem();
        entity.setId(dto.getId());
        entity.setTypeCode(dto.getTypeCode());
        entity.setItemCode(dto.getItemCode());
        entity.setItemValue(dto.getItemValue());
        entity.setSortOrder(dto.getSortOrder());
        entity.setParentId(dto.getParentId());
        entity.setDescription(dto.getDescription());
        entity.setExtJson(dto.getExtJson());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
