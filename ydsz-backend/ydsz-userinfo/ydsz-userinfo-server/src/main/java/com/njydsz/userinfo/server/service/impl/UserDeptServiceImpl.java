package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.UserDeptService;
import com.njydsz.userinfo.domain.entity.UserDeptDO;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeptServiceImpl implements UserDeptService {

    private final UserDeptMapper mapper;

    @Override
    public UserDeptDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<UserDeptDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(UserDeptDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(UserDeptDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
