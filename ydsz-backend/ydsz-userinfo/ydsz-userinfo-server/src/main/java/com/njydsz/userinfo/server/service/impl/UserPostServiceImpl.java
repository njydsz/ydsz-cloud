package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.UserPostService;
import com.njydsz.userinfo.domain.entity.UserPostDO;
import com.njydsz.userinfo.infra.mapper.UserPostMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPostServiceImpl implements UserPostService {

    private final UserPostMapper mapper;

    @Override
    public UserPostDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<UserPostDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(UserPostDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(UserPostDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
