package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.MenuService;
import com.njydsz.userinfo.domain.entity.MenuDO;
import com.njydsz.userinfo.infra.mapper.MenuMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuMapper mapper;

    @Override
    public MenuDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<MenuDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(MenuDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(MenuDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
