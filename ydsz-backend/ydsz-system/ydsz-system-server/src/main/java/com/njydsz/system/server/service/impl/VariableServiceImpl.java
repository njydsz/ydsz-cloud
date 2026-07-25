package com.njydsz.system.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.entity.VariableDO;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.infra.mapper.VariableMapper;
import com.njydsz.system.server.service.VariableService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统变量 Service 实现。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableServiceImpl implements VariableService {

    private final VariableMapper mapper;

    @Override
    public VariableVO getById(String id) {
        VariableDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public IPage<VariableDO> page(int pageNum, int pageSize) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), null);
    }

    @Override
    public List<VariableDO> list() {
        return mapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(VariableDTO dto) {
        VariableDO entity = toEntity(dto);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(VariableDTO dto) {
        VariableDO entity = toEntity(dto);
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }

    private VariableVO toVO(VariableDO entity) {
        if (entity == null) {
            return null;
        }
        VariableVO vo = new VariableVO();
        vo.setId(entity.getId());
        vo.setVariableKey(entity.getVariableKey());
        vo.setVariableValue(entity.getVariableValue());
        vo.setValueType(entity.getValueType());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private VariableDO toEntity(VariableDTO dto) {
        VariableDO entity = new VariableDO();
        entity.setId(dto.getId());
        entity.setVariableKey(dto.getVariableKey());
        entity.setVariableValue(dto.getVariableValue());
        entity.setValueType(dto.getValueType());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
