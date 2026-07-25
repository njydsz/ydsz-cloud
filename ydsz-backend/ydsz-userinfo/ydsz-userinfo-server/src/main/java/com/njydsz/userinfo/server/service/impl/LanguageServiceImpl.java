package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.entity.LanguageDO;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;
import com.njydsz.userinfo.server.service.LanguageService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;

/**
 * 语言 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
        LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LanguageDO::getDeleted, 0);
        wrapper.orderByAsc(LanguageDO::getSortOrder);
        return mapper.selectList(wrapper);
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
