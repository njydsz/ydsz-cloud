package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.common.domain.service.impl.AbstractCrudService;
import com.njydsz.common.domain.specification.Specification;
import com.njydsz.common.jdbc.specification.MyBatisSpecification;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.infra.repository.DictRepository;
import com.njydsz.system.server.service.DictService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典类型 Service 实现。
 *
 * <p>基于 {@link AbstractCrudService} 复用通用 CRUD 能力，
 * 通过生命周期钩子集成 typeCode 唯一性校验。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl
        extends AbstractCrudService<DictTypeDO, DictTypeDTO, DictTypeVO, DictPageQuery, String>
        implements DictService {

    private final DictRepository dictRepository;

    @Override
    protected DictRepository getRepository() {
        return dictRepository;
    }

    @Override
    protected String getId(DictTypeDTO dto) {
        return dto != null ? dto.getId() : null;
    }

    @Override
    protected DictTypeVO toVO(DictTypeDO entity) {
        if (entity == null) {
            return null;
        }
        DictTypeVO vo = new DictTypeVO();
        vo.setId(entity.getId());
        vo.setTypeCode(entity.getTypeCode());
        vo.setTypeName(entity.getTypeName());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    @Override
    protected DictTypeDO toEntity(DictTypeDTO dto) {
        if (dto == null) {
            return null;
        }
        DictTypeDO entity = new DictTypeDO();
        entity.setId(dto.getId());
        entity.setTypeCode(dto.getTypeCode());
        entity.setTypeName(dto.getTypeName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }

    @Override
    protected Specification<DictTypeDO> getPageSpecification(DictPageQuery query) {
        return new MyBatisSpecification<DictTypeDO>() {
            @Override
            public void apply(QueryWrapper<DictTypeDO> wrapper) {
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
            }

            @Override
            public boolean isSatisfiedBy(DictTypeDO candidate) {
                return true;
            }
        };
    }

    @Override
    protected void doBeforeSave(DictTypeDTO dto, DictTypeDO entity) {
        checkDuplicateTypeCode(entity);
    }

    @Override
    protected void doBeforeUpdate(DictTypeDTO dto, DictTypeDO entity) {
        checkDuplicateTypeCode(entity);
    }

    /**
     * {@inheritDoc}
     * <p>查询全部字典类型（不区分状态）。
     */
    @Override
    public List<DictTypeVO> listAll() {
        return dictRepository.getDictTypeMapper().selectList(null).stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 唯一性校验：typeCode 不能重复。
     *
     * @param entity 字典类型实体
     */
    private void checkDuplicateTypeCode(DictTypeDO entity) {
        QueryWrapper<DictTypeDO> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("type_code", entity.getTypeCode());
        if (entity.getId() != null) {
            checkWrapper.ne("id", entity.getId());
        }
        if (dictRepository.getDictTypeMapper().selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException("字典类型编码已存在: " + entity.getTypeCode());
        }
    }
}
