package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper mapper;

    @Override
    public UserAccountDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<UserAccountDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(UserAccountDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(UserAccountDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
