package com.njydsz.system.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.system.server.service.DictService;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.infra.mapper.DictTypeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictTypeMapper mapper;

    @Override
    public DictTypeDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DictTypeDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(DictTypeDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(DictTypeDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
