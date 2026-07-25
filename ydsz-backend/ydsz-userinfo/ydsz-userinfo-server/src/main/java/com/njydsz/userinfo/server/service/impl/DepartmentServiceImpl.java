package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.userinfo.domain.entity.DepartmentDO;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper mapper;

    @Override
    public DepartmentDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DepartmentDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(DepartmentDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(DepartmentDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
