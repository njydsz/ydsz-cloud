package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.CompanyDeptService;
import com.njydsz.userinfo.domain.entity.CompanyDeptDO;
import com.njydsz.userinfo.infra.mapper.CompanyDeptMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyDeptServiceImpl implements CompanyDeptService {

    private final CompanyDeptMapper mapper;

    @Override
    public CompanyDeptDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<CompanyDeptDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(CompanyDeptDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(CompanyDeptDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
