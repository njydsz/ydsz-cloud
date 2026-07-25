package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.RoleService;
import com.njydsz.userinfo.domain.entity.RoleDO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper mapper;

    @Override
    public RoleDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<RoleDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(RoleDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(RoleDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
