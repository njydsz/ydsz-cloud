package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.UserFieldService;
import com.njydsz.userinfo.domain.entity.UserFieldDO;
import com.njydsz.userinfo.infra.mapper.UserFieldMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFieldServiceImpl implements UserFieldService {

    private final UserFieldMapper mapper;

    @Override
    public UserFieldDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<UserFieldDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(UserFieldDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(UserFieldDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
