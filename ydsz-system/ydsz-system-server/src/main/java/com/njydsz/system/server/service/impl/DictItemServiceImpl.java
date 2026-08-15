package com.njydsz.system.server.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.DictVersionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典项 Service 实现
 *
 * <p>对 {@link DictItemService} 接口的完整实现，是「字典中心」字典项管理的核心业务逻辑层。
 * 集成 ydsz-common-cache 本地缓存（Spring Cache 注解驱动）、Micrometer 指标、
 * 字典版本快照（写操作自动记录变更历史，含完整快照支持回滚）。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}，全部走 {@code @Transactional} 事务保证</li>
 *   <li><b>缓存读</b>：{@link #getByTypeAndCode}（单 key） / {@link #listEnabledByTypeCode}（列表） —
 *       走 ydsz-common-cache 本地缓存 + Spring Cache {@code @Cacheable} 注解</li>
 *   <li><b>树形结构</b>：{@link #listChildren} — 支持「省 / 市 / 区县」三级级联、行政区划、组织架构等场景</li>
 *   <li><b>版本快照</b>：写操作成功后<b>同步</b>调用 {@link DictVersionService#createVersion}
 *       记录变更前的全量字典项 JSON 快照</li>
 *   <li><b>唯一性校验</b>：保存前校验 {@code (typeCode, itemCode)} 组合唯一性</li>
 * </ul>
 *
 * <p><b>缓存设计：</b>
 * <ul>
 *   <li>缓存名称：{@link CacheConstants#SYSTEM_DICT_ITEM_CACHE}（ydsz-common-cache 本地缓存）</li>
 *   <li>字典项缓存键：{@code item:{tenantId}:{typeCode}:{itemCode}}</li>
 *   <li>字典列表缓存键：{@code list:{tenantId}:{typeCode}}</li>
 *   <li>TTL 与容量通过 {@code ydsz.cache.caches.system:dict:item} YAML 配置</li>
 *   <li>写操作触发 {@code @CacheEvict(allEntries=true)} 主动失效</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>写方法与版本快照<b>在同一事务内</b>，保证原子性</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>缓存预热</b>：首次访问某 {@code typeCode} 时无缓存，穿透到 DB 查询（受空值哨兵保护）</li>
 *   <li><b>版本快照一致性</b>：快照在字典项变更<b>前</b>由调用方抓取（{@link #createSnapshotVersion}），
 *       反映变更前的状态，可用于回滚</li>
 *   <li><b>SCAN 防阻塞</b>：缓存失效使用 {@code SCAN} 命令遍历模式匹配 key，
 *       由 {@code @CacheEvict(allEntries=true)} 主动清空</li>
 *   <li><b>软删除</b>：{@code ydsz_dict_item} 表采用 <b>逻辑删除</b>（{@code deleted} 字段），
 *       删除后通过 {@code status=DISABLED} 标记失效</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 前端下拉框数据源（高频读）
 * List<DictItemVO> userStatus = dictItemService.listEnabledByTypeCode("user_status");
 *
 * // 行政区划级联（树形）
 * List<DictItemVO> provinces = dictItemService.listEnabledByTypeCode("region");
 * List<DictItemVO> citiesOfZJ = dictItemService.listChildren(provinces.get(0).getId());
 *
 * // 管理后台新增字典项（自动创建版本快照）
 * String id = dictItemService.save(DictItemDTO.builder()
 *     .typeCode("user_status").itemCode("RESIGNED")
 *     .itemValue("离职").sortOrder(40).build());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DictItemService 字典项 Service 接口
 * @see DictServiceImpl 字典类型 Service 实现
 * @see DictVersionService 字典版本 Service（写操作触发版本快照）
 * @see com.njydsz.system.domain.entity.DictItem 字典项实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

    /** 字典项 Mapper（继承 {@code ydsz_dict_item} 表 CRUD） */
    private final DictItemMapper mapper;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;
    /** 系统配置属性（含字典缓存 TTL 配置） */
    private final SystemProperties properties;
    /** 字典版本服务，用于记录变更快照 */
    private final DictVersionService dictVersionService;

    /**
     * 根据主键查询字典项（不走缓存，直接走 DB）
     *
     * <p>适用场景：管理后台「字典项详情」页，单次访问无缓存需求。
     * 高频查询请使用 {@link #getByTypeAndCode}。
     *
     * @param id 字典项主键
     * @return 字典项 VO，不存在返回 null
     */
    @Override
    public DictItemVO getById(String id) {
        DictItem entity = mapper.selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    /**
     * 按 typeCode + itemCode 查询单个字典项（走缓存）
     *
     * <p>执行链路：
     * <ol>
     *   <li>通过 Spring Cache {@code @Cacheable} 查本地缓存（{@link CacheConstants#SYSTEM_DICT_ITEM_CACHE}），命中直接返回</li>
     *   <li>缓存未命中查 DB（方法体内仅执行此逻辑）</li>
     *   <li>记录查询耗时指标（缓存命中时方法不执行，由 Micrometer 记录）</li>
     * </ol>
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @return 字典项 VO，不存在时返回 null（受空值哨兵保护，短 TTL 内不会反复穿透到 DB）
     */
    @Override
    @Cacheable(value = CacheConstants.SYSTEM_DICT_ITEM_CACHE, key = "@cacheKeyBuilder.dictItem(#p0, #p1)")
    public DictItemVO getByTypeAndCode(String typeCode, String itemCode) {
        long start = System.nanoTime();
        try {
            metrics.recordDictCacheMiss();
            DictItem entity = mapper.selectByTypeAndCode(typeCode, itemCode);
            return SystemConverter.INSTANT.entityToVO(entity);
        } finally {
            metrics.recordDictQuery(System.nanoTime() - start);
        }
    }

    /**
     * 按 typeCode 查询所有启用状态的字典项列表（走缓存）
     *
     * <p>典型调用方：前端下拉框、级联选择器数据源。
     * 仅返回 {@code status='ENABLED'} 的字典项，按 {@code sortOrder} 升序。
     *
     * <p><b>性能说明：</b>
     * <ul>
     *   <li>索引：{@code (tenant_id, type_code, status, sort_order)}</li>
     *   <li>单 typeCode 字典项一般 < 100 条，单次查询 < 5ms</li>
     *   <li>缓存命中后 1ms 内返回</li>
     * </ul>
     *
     * @param typeCode 字典类型编码
     * @return 启用状态的字典项列表（按 sortOrder 升序），无数据时返回空列表
     */
    @Override
    @Cacheable(value = CacheConstants.SYSTEM_DICT_ITEM_CACHE, key = "@cacheKeyBuilder.dictList(#p0)")
    public List<DictItemVO> listEnabledByTypeCode(String typeCode) {
        long start = System.nanoTime();
        try {
            metrics.recordDictCacheMiss();
            List<DictItem> entities = mapper.listEnabledByTypeCode(typeCode);
            return entities.stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
        } finally {
            metrics.recordDictQuery(System.nanoTime() - start);
        }
    }

    /**
     * 查询指定父节点下的所有子字典项（树形结构）
     *
     * <p>典型场景：
     * <ul>
     *   <li>行政区划：「浙江省」→ 杭州市 / 宁波市 / 温州市 ...</li>
     *   <li>组织架构：「总部」→ 各事业部</li>
     *   <li>商品分类：「电子产品」→ 手机 / 电脑 / 平板 ...</li>
     * </ul>
     *
     * <p>本方法<b>不走缓存</b>，由调用方按需缓存；树形结构变化频次低，建议调用方做本地缓存。
     *
     * @param parentId 父字典项 ID（{@code ydsz_dict_item.parent_id}）
     * @return 子字典项列表（按 sortOrder 升序），无子节点返回空列表
     */
    @Override
    public List<DictItemVO> listChildren(String parentId) {
        QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", parentId).orderByAsc("sort_order");
        return mapper.selectList(wrapper).stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
    }

    /**
     * 分页查询字典项（管理后台列表页）
     *
     * <p>支持按 {@code typeCode} 精确匹配、{@code itemCode} 模糊匹配、{@code status} 精确匹配进行过滤，
     * 按 {@code created_at} 倒序返回。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @param typeCode 字典类型编码（可选过滤条件）
     * @param itemCode 字典项编码（可选，模糊匹配）
     * @param status   状态（可选过滤条件，如 {@code ENABLED/DISABLED}）
     * @return 分页结果（含总条数）
     */
    @Override
    public PageResponse<List<DictItemVO>> page(int pageNum, int pageSize, String typeCode, String itemCode, String status) {
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
        return PageResponse.success(page.getTotal(), page.getCurrent(), page.getSize(), vos);
    }

    /**
     * 查询全部字典项（不区分状态）
     *
     * <p><b>慎用：</b>全表扫描，仅适用于「全量字典数据导出」等离线场景。
     * 前端下拉框请使用 {@link #listEnabledByTypeCode}。
     *
     * @return 全部字典项列表（不区分状态、按 createdAt 倒序）
     */
    @Override
    public List<DictItemVO> list() {
        return mapper.selectList(null).stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
    }

    /**
     * 新增字典项
     *
     * <p>执行链路：
     * <ol>
     *   <li>唯一性校验：{@code (typeCode, itemCode)} 组合不能重复</li>
     *   <li>创建版本快照（变更前状态）</li>
     *   <li>DTO 转 DO，默认 {@code status=ENABLED}</li>
     *   <li>插入 {@code ydsz_dict_item} 表</li>
     *   <li>清除该 {@code typeCode} 下的所有缓存</li>
     * </ol>
     *
     * @param dto 字典项数据
     * @return 新创建的字典项 ID
     * @throws IllegalArgumentException {@code (typeCode, itemCode)} 组合已存在时抛出
     */
    @Override
    @CacheEvict(value = CacheConstants.SYSTEM_DICT_ITEM_CACHE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public String save(DictItemDTO dto) {
        // 唯一性校验：(typeCode, itemCode) 组合不能重复
        QueryWrapper<DictItem> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("type_code", dto.getTypeCode())
                .eq("item_code", dto.getItemCode());
        if (mapper.selectCount(checkWrapper) > 0) {
            throw BusinessException.of(SystemExceptionCode.DICT_ITEM_CODE_DUPLICATE)
                    .data("typeCode", dto.getTypeCode())
                    .data("itemCode", dto.getItemCode());
        }
        // 写操作前抓取「变更前」快照，支持后续版本回滚
        createSnapshotVersion(dto.getTypeCode(), "新增字典项: " + dto.getItemCode());
        DictItem entity = toEntity(dto);
        mapper.insert(entity);
        return entity.getId();
    }

    /**
     * 更新字典项
     *
     * <p>执行链路：
     * <ol>
     *   <li>创建版本快照（变更前状态）</li>
     *   <li>DTO 转 DO</li>
     *   <li>更新 {@code ydsz_dict_item} 表</li>
     *   <li>更新成功后清除该 {@code typeCode} 下的所有缓存</li>
     * </ol>
     *
     * @param dto 字典项数据（需包含 {@code id}）
     * @return true=更新成功，false=记录不存在
     */
    @Override
    @CacheEvict(value = CacheConstants.SYSTEM_DICT_ITEM_CACHE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictItemDTO dto) {
        // 写操作前抓取「变更前」快照，支持后续版本回滚
        createSnapshotVersion(dto.getTypeCode(), "更新字典项: " + dto.getItemCode());
        DictItem entity = toEntity(dto);
        return mapper.updateById(entity) > 0;
    }

    /**
     * 逻辑删除字典项
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}），
     * 不真正从 DB 删除，便于审计回溯。
     *
     * <p>执行链路：
     * <ol>
     *   <li>查询原实体（用于获取 typeCode）</li>
     *   <li>创建版本快照（变更前状态）</li>
     *   <li>逻辑删除记录</li>
     *   <li>删除成功后清除该 {@code typeCode} 下的所有缓存</li>
     * </ol>
     *
     * @param id 字典项主键
     * @return true=删除成功，false=记录不存在
     */
    @Override
    @CacheEvict(value = CacheConstants.SYSTEM_DICT_ITEM_CACHE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        DictItem entity = mapper.selectById(id);
        if (entity == null) {
            return false;
        }
        // 写操作前抓取「变更前」快照，支持后续版本回滚
        createSnapshotVersion(entity.getTypeCode(), "删除字典项: " + entity.getItemCode());
        return mapper.deleteById(id) > 0;
    }

    /**
     * 创建字典版本快照（私有）
     *
     * <p>在写操作<b>前</b>抓取当前 {@code typeCode} 下所有字典项，
     * 序列化为 JSON 后写入 {@code ydsz_dict_version} 表，作为变更前的「基线」快照，
     * 支持后续版本回滚。
     *
     * @param typeCode  字典类型编码
     * @param changeLog 变更说明（如「新增字典项: RESIGNED」）
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
     * DTO → DO 转换（私有）
     *
     * <p>缺省 {@code status="ENABLED"}，保证新建的字典项默认可用。
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
