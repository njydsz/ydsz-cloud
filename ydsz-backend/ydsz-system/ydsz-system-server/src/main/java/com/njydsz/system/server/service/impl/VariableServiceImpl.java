package com.njydsz.system.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.system.server.service.VariableService;
import com.njydsz.system.domain.entity.VariableDO;
import com.njydsz.system.infra.mapper.VariableMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VariableServiceImpl implements VariableService {

    private final VariableMapper mapper;

    @Override
    public VariableDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<VariableDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(VariableDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(VariableDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
