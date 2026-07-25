package com.njydsz.system.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.infra.mapper.ConfigMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final ConfigMapper mapper;

    @Override
    public ConfigDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<ConfigDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(ConfigDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(ConfigDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
