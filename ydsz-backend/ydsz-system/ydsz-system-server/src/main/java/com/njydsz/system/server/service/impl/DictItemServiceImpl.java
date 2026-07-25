package com.njydsz.system.server.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.domain.entity.DictItemDO;
import com.njydsz.system.infra.mapper.DictItemMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

    private final DictItemMapper mapper;

    @Override
    public DictItemDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<DictItemDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(DictItemDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(DictItemDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
