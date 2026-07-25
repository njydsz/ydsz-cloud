package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.CompanyService;
import com.njydsz.userinfo.domain.entity.CompanyDO;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper mapper;

    @Override
    public CompanyDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<CompanyDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(CompanyDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(CompanyDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
