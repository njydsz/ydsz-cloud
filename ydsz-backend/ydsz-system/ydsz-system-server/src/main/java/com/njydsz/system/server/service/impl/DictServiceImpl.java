package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.infra.mapper.DictTypeMapper;
import com.njydsz.system.server.service.DictService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典类型 Service 实现。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    /** 字典类型 Mapper */
    private final DictTypeMapper mapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public DictTypeVO getById(String id) {
        DictTypeDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>支持按 typeName 模糊匹配、status 精确匹配过滤。
     */
    @Override
    public IPage<DictTypeVO> page(int pageNum, int pageSize, String typeName, String status) {
        QueryWrapper<DictTypeDO> wrapper = new QueryWrapper<>();
        if (typeName != null && !typeName.isBlank()) {
            wrapper.like("type_name", typeName);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        IPage<DictTypeDO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<DictTypeVO> vos = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        Page<DictTypeVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部字典类型列表（不区分状态）
     */
    @Override
    public List<DictTypeVO> list() {
        return mapper.selectList(null).stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行 typeCode 唯一性校验后插入。
     *
     * @throws IllegalArgumentException 当 typeCode 已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DictTypeDTO dto) {
        // 唯一性校验：typeCode 不能重复
        QueryWrapper<DictTypeDO> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("type_code", dto.getTypeCode());
        if (mapper.selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException("字典类型编码已存在: " + dto.getTypeCode());
        }
        DictTypeDO entity = toEntity(dto);
        mapper.insert(entity);
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictTypeDTO dto) {
        DictTypeDO entity = toEntity(dto);
        return mapper.updateById(entity) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }

    /**
     * 将 DO 转换为 VO。
     *
     * @param entity 数据库实体
     * @return 视图对象，entity 为 null 时返回 null
     */
    private DictTypeVO toVO(DictTypeDO entity) {
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

    /**
     * 将 DTO 转换为 DO，status 为空时默认 ENABLED。
     *
     * @param dto 数据传输对象
     * @return 数据库实体
     */
    private DictTypeDO toEntity(DictTypeDTO dto) {
        DictTypeDO entity = new DictTypeDO();
        entity.setId(dto.getId());
        entity.setTypeCode(dto.getTypeCode());
        entity.setTypeName(dto.getTypeName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
