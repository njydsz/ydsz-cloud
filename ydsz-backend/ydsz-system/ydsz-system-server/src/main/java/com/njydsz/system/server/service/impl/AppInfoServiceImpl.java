package com.njydsz.system.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.system.server.service.AppInfoService;
import com.njydsz.system.domain.entity.AppInfoDO;
import com.njydsz.system.infra.mapper.AppInfoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppInfoServiceImpl implements AppInfoService {

    private final AppInfoMapper mapper;

    @Override
    public AppInfoDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<AppInfoDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(AppInfoDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(AppInfoDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
