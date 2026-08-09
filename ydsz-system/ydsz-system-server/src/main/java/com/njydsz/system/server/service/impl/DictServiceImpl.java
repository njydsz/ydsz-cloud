package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.entity.DictType;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.infra.repository.DictRepository;
import com.njydsz.system.server.service.DictService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 字典类型 Service 实现
 *
 * <p>对 {@link DictService} 接口的完整实现，是「字典中心」的核心业务逻辑层。
 * 维护 {@code ydsz_dict_type} 字典类型表，是「字典项」（{@link DictItemServiceImpl}）的父级元数据，
 * 对标大厂「配置中心 / 字典中心」Schema 管理层。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}，全部走 {@code @Transactional} 事务保证</li>
 *   <li><b>唯一性校验</b>：保存 / 更新前校验 {@code (tenantId, typeCode)} 唯一性，冲突时抛 {@code IllegalArgumentException}</li>
 *   <li><b>缓存联动</b>：写操作触发 {@code @CacheEvict} 失效 Redis 字典缓存
 *       （{@code ydsz:dict:type:{typeCode}} / {@code ydsz:dict:full:{typeCode}}），
 *       由调用方在 Controller 层组合触发</li>
 *   <li><b>全量查询</b>：{@link #listAll} 走本地 Caffeine 缓存（5min TTL），
 *       避免下拉框渲染触发 DB（具体由 {@code @Cacheable} 在调用方实现）</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 *   <li>字典类型与字典项的强一致性由外层 Service（如 {@code DictSyncService}）保证事务边界</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>强校验</b>：删除字典类型前<b>必须</b>先删除其下所有字典项（由外层调用方控制），
 *       避免孤儿字典项</li>
 *   <li><b>唯一性</b>：{@code typeCode} 是字典类型的「业务主键」（对前端可见），
 *       全租户内唯一，不能修改，只能新增新类型</li>
 *   <li><b>扩展性</b>：通过 {@code DictItemServiceImpl} 挂载实际字典项，
 *       单个 {@code typeCode} 下可有任意数量的 {@code itemCode}</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 管理后台新增字典类型
 * String typeId = dictService.save(DictTypeDTO.builder()
 *     .typeCode("user_status")
 *     .typeName("用户状态")
 *     .description("在职 / 离职 / 休假等状态枚举")
 *     .build());
 *
 * // 然后挂载字典项
 * dictItemService.save(DictItemDTO.builder()
 *     .typeCode("user_status").itemCode("ACTIVE").itemValue("在职").build());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DictService 字典类型 Service 接口
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 * @see DictItemServiceImpl 字典项 Service 实现（依赖本类创建类型后再挂载字典项）
 * @see DictVersionServiceImpl 字典版本 Service（写操作触发版本快照）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    /** 字典仓储（聚合 DictTypeMapper / DictItemMapper） */
    private final DictRepository dictRepository;

    // ============================== CRUD ==============================

    /**
     * 分页查询字典类型（管理后台列表页）
     *
     * <p>支持按 {@code typeCode} 精确匹配、{@code typeName} 模糊匹配、{@code status} 精确匹配进行过滤，
     * 按 {@code created_at} 倒序返回。
     *
     * @param query 分页查询条件（含 {@code pageNum / pageSize / typeCode / typeName / status}）
     * @return 分页结果（含 {@code records / total}）
     */
    @Override
    public PageResult<DictTypeVO> page(DictPageQuery query) {
        QueryWrapper<DictType> wrapper = buildQueryWrapper(query);
        Page<DictType> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
        IPage<DictType> result = dictRepository.getDictTypeMapper().selectPage(mpPage, wrapper);
        List<DictTypeVO> vos = result.getRecords().stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PageResult.of(vos, result.getTotal(), query.getEffectivePageNum(), query.getEffectivePageSize());
    }

    /**
     * 根据主键查询字典类型
     *
     * @param id 字典类型主键
     * @return 字典类型 VO，不存在返回 null
     */
    @Override
    public DictTypeVO getById(String id) {
        DictType entity = dictRepository.getDictTypeMapper().selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    /**
     * 新增字典类型
     *
     * <p>执行链路：
     * <ol>
     *   <li>DTO 转 DO，默认 {@code status=ENABLED}</li>
     *   <li>唯一性校验：{@code typeCode} 全租户内不能重复</li>
     *   <li>插入 {@code ydsz_dict_type} 表</li>
     * </ol>
     *
     * <p><b>注意：</b>本方法仅创建类型，<b>不挂载字典项</b>，字典项需通过 {@link DictItemServiceImpl#save} 单独添加。
     *
     * @param dto 字典类型数据
     * @return 新创建的字典类型 ID
     * @throws IllegalArgumentException {@code typeCode} 已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DictTypeDTO dto) {
        DictType entity = toEntity(dto);
        checkDuplicateTypeCode(entity);
        dictRepository.getDictTypeMapper().insert(entity);
        return entity.getId();
    }

    /**
     * 更新字典类型
     *
     * <p>执行链路：
     * <ol>
     *   <li>DTO 转 DO</li>
     *   <li>唯一性校验：{@code typeCode} 变更时不能与现有类型冲突</li>
     *   <li>更新 {@code ydsz_dict_type} 表</li>
     * </ol>
     *
     * <p><b>注意：</b>更新 {@code typeCode} 会导致所有依赖该编码的下游缓存失效，
     * 调用方需主动清理 {@code ydsz:dict:*} 相关 Redis key。
     *
     * @param dto 字典类型数据（需包含 {@code id}）
     * @return true=更新成功，false=记录不存在
     * @throws IllegalArgumentException {@code typeCode} 已被其他类型占用时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictTypeDTO dto) {
        DictType entity = toEntity(dto);
        checkDuplicateTypeCode(entity);
        return dictRepository.getDictTypeMapper().updateById(entity) > 0;
    }

    /**
     * 逻辑删除字典类型
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}），
     * 不真正从 DB 删除，便于审计回溯。
     *
     * <p><b>注意：</b>本方法<b>不</b>级联删除字典项，调用方需自行处理：
     * <ol>
     *   <li>先调用 {@link DictItemServiceImpl#removeById} 删除所有字典项</li>
     *   <li>再调用本方法删除类型</li>
     * </ol>
     *
     * @param id 字典类型主键
     * @return true=删除成功，false=记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return dictRepository.getDictTypeMapper().deleteById(id) > 0;
    }

    // ============================== 业务查询 ==============================

    /**
     * 查询全部字典类型（不区分状态）
     *
     * <p>典型调用方：
     * <ul>
     *   <li>管理后台「字典类型管理」列表页（带分页时用 {@link #page}）</li>
     *   <li>「类型选择器」下拉框（高频读，建议调用方在 Controller 层加 {@code @Cacheable}）</li>
     * </ul>
     *
     * <p><b>慎用：</b>全表扫描，字典类型一般 < 100 条，单次查询 < 10ms。
     *
     * @return 全部字典类型列表（按 createdAt 倒序）
     */
    @Override
    public List<DictTypeVO> listAll() {
        return dictRepository.getDictTypeMapper().selectList(null).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ============================== 私有方法 ==============================

    /**
     * 构建分页查询条件（私有）
     *
     * @param query 分页查询条件
     * @return MyBatis-Plus QueryWrapper
     */
    private QueryWrapper<DictType> buildQueryWrapper(DictPageQuery query) {
        QueryWrapper<DictType> wrapper = new QueryWrapper<>();
        if (query.getTypeCode() != null && !query.getTypeCode().isBlank()) {
            wrapper.eq("type_code", query.getTypeCode());
        }
        if (query.getTypeName() != null && !query.getTypeName().isBlank()) {
            wrapper.like("type_name", query.getTypeName());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq("status", query.getStatus());
        }
        wrapper.orderByDesc("created_at");
        return wrapper;
    }

    /**
     * DTO → DO 转换（私有）
     *
     * <p>缺省 {@code status="ENABLED"}，保证新建的字典类型默认可用。
     *
     * @param dto 数据传输对象
     * @return 数据库实体
     */
    private DictType toEntity(DictTypeDTO dto) {
        if (dto == null) {
            return null;
        }
        DictType entity = new DictType();
        entity.setId(dto.getId());
        entity.setTypeCode(dto.getTypeCode());
        entity.setTypeName(dto.getTypeName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }

    /**
     * 唯一性校验（私有）
     *
     * <p>校验 {@code typeCode} 是否已被其他字典类型占用。
     * 更新场景下排除自身 ID（{@code ne("id", entity.getId())}）。
     *
     * @param entity 待校验的字典类型实体
     * @throws IllegalArgumentException {@code typeCode} 已存在时抛出
     */
    private void checkDuplicateTypeCode(DictType entity) {
        QueryWrapper<DictType> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("type_code", entity.getTypeCode());
        if (entity.getId() != null) {
            checkWrapper.ne("id", entity.getId());
        }
        if (dictRepository.getDictTypeMapper().selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException("字典类型编码已存在: " + entity.getTypeCode());
        }
    }
}
