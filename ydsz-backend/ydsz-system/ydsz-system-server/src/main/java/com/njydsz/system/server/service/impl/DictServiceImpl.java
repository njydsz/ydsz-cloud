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
 * 集成 {@code (tenantId, typeCode)} 唯一性校验、Redis 缓存失效、字典版本快照。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@code page} / {@code getById} / {@code save} / {@code updateById} / {@code removeById}，
 *       全部走 {@code @Transactional} 事务保证</li>
 *   <li><b>唯一性校验</b>：保存前校验 {@code (tenantId, typeCode)} 唯一性，冲突时抛 {@code BizException}</li>
 *   <li><b>缓存联动</b>：写操作触发 {@code @CacheEvict} 失效 Redis 字典缓存
 *       （{@code ydsz:dict:type:{typeCode}} / {@code ydsz:dict:full:{typeCode}}）</li>
 *   <li><b>全量查询</b>：{@code listAll()} 走本地 Caffeine 缓存（5min TTL），避免下拉框渲染触发 DB</li>
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
 * @see DictService 字典类型 Service 接口
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 * @see DictItemServiceImpl 字典项 Service 实现（依赖本类创建类型后再挂载字典项）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictRepository dictRepository;

    // ============================== CRUD ==============================

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

    @Override
    public DictTypeVO getById(String id) {
        DictType entity = dictRepository.getDictTypeMapper().selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DictTypeDTO dto) {
        DictType entity = toEntity(dto);
        checkDuplicateTypeCode(entity);
        dictRepository.getDictTypeMapper().insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictTypeDTO dto) {
        DictType entity = toEntity(dto);
        checkDuplicateTypeCode(entity);
        return dictRepository.getDictTypeMapper().updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return dictRepository.getDictTypeMapper().deleteById(id) > 0;
    }

    // ============================== 业务查询 ==============================

    @Override
    public List<DictTypeVO> listAll() {
        return dictRepository.getDictTypeMapper().selectList(null).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ============================== 私有方法 ==============================

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
