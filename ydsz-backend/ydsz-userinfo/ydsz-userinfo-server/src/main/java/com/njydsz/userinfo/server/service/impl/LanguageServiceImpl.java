package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.LanguageService;
import com.njydsz.userinfo.domain.entity.LanguageDO;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageServiceImpl implements LanguageService {

    private final LanguageMapper mapper;

    @Override
    public LanguageDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<LanguageDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(LanguageDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(LanguageDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
